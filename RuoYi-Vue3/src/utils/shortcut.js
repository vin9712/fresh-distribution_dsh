/**
 * 快捷键集中注册服务（W0-5.2，蓝图 §3.3）
 *
 * 规则：
 * - 全局唯一 window keydown 监听（懒安装），所有快捷键经此处分发
 * - 作用域优先级：GLOBAL > WORKSPACE > TABLE，同键多作用域注册时高优先级先分发
 * - 分发为责任链：handler 返回 false 表示「我不处理，继续下传」，返回其余值/undefined 表示已处理（自动 preventDefault + stopPropagation）
 * - 中文输入法组合态兼容：isComposing / keyCode 229 一律不分发（蓝图「全局搜索不在中文输入组合态触发」）
 * - 输入焦点保护：目标为 input/textarea/select/contenteditable 时默认不分发，
 *   例外：带 ctrl/meta 修饰键的组合（录入惯例），或绑定显式 allowInInput（如 Esc 关弹窗）
 * - keep-alive 页面：由 useShortcuts 组合式函数在 onActivated/onDeactivated 中启停
 *
 * 用法（组合式 API，推荐）：
 *   import { useShortcuts } from '@/composables/useShortcuts'
 *   useShortcuts([
 *     { scope: SCOPE.WORKSPACE, key: 'f2', description: '新增', handler: onAdd }
 *   ])
 *
 * 用法（选项式 API）：
 *   import { registerShortcut } from '@/utils/shortcut'
 *   mounted()  { this._unregF2 = registerShortcut({ scope: SCOPE.TABLE, key: 'f2', handler: this.onAdd }) }
 *   beforeUnmount() { this._unregF2 && this._unregF2() }
 */
const isDev = import.meta.env.DEV

/** 作用域常量；数值越小优先级越高 */
export const SCOPE = {
  GLOBAL: 'global',
  WORKSPACE: 'workspace',
  TABLE: 'table'
}

const SCOPE_RANK = { [SCOPE.GLOBAL]: 0, [SCOPE.WORKSPACE]: 1, [SCOPE.TABLE]: 2 }

const registry = new Map() // id -> binding
let idSeq = 0
let listenerInstalled = false

// ---------------- 事件匹配 ----------------

function isEditableTarget(target) {
  if (!target) return false
  const tag = target.tagName || ''
  return (
    tag === 'INPUT' ||
    tag === 'TEXTAREA' ||
    tag === 'SELECT' ||
    target.isContentEditable === true
  )
}

/** 解析 'ctrl+k' / 'shift+delete' / 'f2' 形式的绑定键 */
function parseBindingKey(combo) {
  const parts = String(combo || '')
    .toLowerCase()
    .split('+')
    .map((s) => s.trim())
    .filter(Boolean)
  const main = parts[parts.length - 1]
  return {
    key: main,
    ctrl: parts.includes('ctrl') || parts.includes('mod'),
    meta: parts.includes('meta') || parts.includes('cmd'),
    shift: parts.includes('shift'),
    alt: parts.includes('alt')
  }
}

/** 事件主键归一化：字母不区分大小写；空格统一 ' ' */
function eventKey(e) {
  const k = String(e.key || '')
  if (k === ' ') return 'space'
  return k.length === 1 ? k.toLowerCase() : k.toLowerCase()
}

function eventMatches(e, parsed) {
  if (eventKey(e) !== parsed.key) return false
  const ctrlOrMeta = parsed.ctrl || parsed.meta
  const eCtrl = e.ctrlKey || e.metaKey
  if (ctrlOrMeta !== eCtrl) return false
  if (parsed.shift !== !!e.shiftKey) return false
  if (parsed.alt !== !!e.altKey) return false
  return true
}

/** 绑定是否含 ctrl/meta 修饰键（输入框内仍响应的组合） */
function hasModifier(parsed) {
  return parsed.ctrl || parsed.meta
}

// ---------------- 分发 ----------------

function onKeyDown(e) {
  // 中文输入法组合态：不分发任何快捷键（蓝图硬性要求）
  if (e.isComposing || e.keyCode === 229) return

  const inInput = isEditableTarget(e.target)

  // 收集匹配且启用的绑定，按作用域优先级排序（同作用域按注册先后）
  const matches = []
  registry.forEach((b) => {
    if (!b.enabled) return
    if (!eventMatches(e, b.parsed)) return
    if (inInput && !b.allowInInput && !hasModifier(b.parsed)) return
    matches.push(b)
  })
  if (!matches.length) return

  matches.sort(
    (a, b) =>
      SCOPE_RANK[a.scope] - SCOPE_RANK[b.scope] || a.seq - b.seq
  )

  for (const b of matches) {
    const result = b.handler(e)
    if (result === false) continue // 显式下传给低优先级绑定
    e.preventDefault()
    e.stopPropagation()
    return
  }
}

function installListener() {
  if (listenerInstalled) return
  window.addEventListener('keydown', onKeyDown)
  listenerInstalled = true
}

function uninstallListenerIfEmpty() {
  if (registry.size === 0 && listenerInstalled) {
    window.removeEventListener('keydown', onKeyDown)
    listenerInstalled = false
  }
}

// ---------------- 注册/注销 ----------------

/**
 * 注册一个快捷键绑定，返回注销函数。
 * binding: {
 *   scope: SCOPE.*                    // 作用域
 *   key: 'ctrl+k' | 'f2' | 'escape'   // 组合键
 *   handler: (event) => boolean|void  // 返回 false 表示下传低优先级
 *   description: string               // 冲突表用中文说明
 *   allowInInput?: boolean            // 输入框聚焦时仍响应（默认 false；ctrl/meta 组合天然响应）
 *   owner?: string                    // 注册来源标识（组件/页面名），冲突表用
 * }
 */
export function registerShortcut(binding) {
  const id = ++idSeq
  const record = {
    id,
    seq: id,
    scope: binding.scope || SCOPE.WORKSPACE,
    combo: String(binding.key || '').toLowerCase(),
    parsed: parseBindingKey(binding.key),
    handler: binding.handler,
    description: binding.description || '',
    owner: binding.owner || '',
    allowInInput: !!binding.allowInInput,
    enabled: binding.enabled !== false
  }
  registry.set(id, record)

  // 开发环境重复注册提示（同作用域同键）
  if (isDev) {
    registry.forEach((other) => {
      if (other.id !== id && other.scope === record.scope && other.combo === record.combo) {
        console.warn(
          `[shortcut] 冲突：${record.combo} 在 ${record.scope} 作用域重复注册 ` +
            `(${other.owner || other.id} / ${record.owner || id})，后注册者仅在前者返回 false 时生效`
        )
      }
    })
  }

  installListener()

  return function unregister() {
    registry.delete(id)
    uninstallListenerIfEmpty()
  }
}

/**
 * 批量注册，返回注销函数集合。
 */
export function registerShortcuts(bindings) {
  const unregs = bindings.map((b) => registerShortcut(b))
  return function unregisterAll() {
    unregs.forEach((fn) => fn())
  }
}

/** 启停某注册来源（owner 标识）下的全部绑定——keep-alive 失活时使用 */
export function setEnabledByOwner(owner, enabled) {
  registry.forEach((b) => {
    if (b.owner === owner) b.enabled = enabled
  })
}

/** 导出当前注册表（生成快捷键冲突表/文档用） */
export function getShortcutRegistry() {
  return Array.from(registry.values()).map(({ parsed, seq, ...rest }) => ({ ...rest }))
}

/** 找出同作用域同键的重复注册（冲突表自动核对） */
export function findShortcutConflicts() {
  const seen = new Map()
  const conflicts = []
  registry.forEach((b) => {
    const k = `${b.scope}:${b.combo}`
    if (seen.has(k)) conflicts.push([seen.get(k), b])
    else seen.set(k, b)
  })
  return conflicts
}

/**
 * useShortcuts — 快捷键组合式封装（W0-5.2）
 *
 * - onMounted 注册、onBeforeUnmount 注销
 * - keep-alive 缓存页：onActivated 启用、onDeactivated 停用（蓝图「仅由当前活动工作区响应」）
 *
 * 示例：
 *   useShortcuts([
 *     { scope: SCOPE.WORKSPACE, key: 'f2', description: '切换右侧面板', owner: 'DemoSplitWorkspace', handler }
 *   ])
 */
import { onActivated, onBeforeUnmount, onDeactivated, onMounted } from 'vue'
import { registerShortcuts, setEnabledByOwner } from '@/utils/shortcut'

let ownerSeq = 0

export function useShortcuts(bindings, { autoOwner = true } = {}) {
  const owner = autoOwner ? `useShortcuts#${++ownerSeq}` : ''
  const list = bindings.map((b) => (autoOwner && !b.owner ? { ...b, owner } : b))

  let unregister = null
  onMounted(() => {
    unregister = registerShortcuts(list)
  })
  onBeforeUnmount(() => {
    if (unregister) unregister()
    unregister = null
  })

  // keep-alive：失活的工作区不应响应快捷键
  if (autoOwner) {
    onActivated(() => setEnabledByOwner(owner, true))
    onDeactivated(() => setEnabledByOwner(owner, false))
  }

  return owner
}

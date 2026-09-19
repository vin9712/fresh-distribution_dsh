/**
 * 销售订单草稿自动保存工具（localStorage + 后端双写，后端为主、本地兜底断网）
 * 设计：docs/交互优化开发计划.md Phase 1.1；治理改造：客户端化ERP改善计划 W0-5.3（蓝图 §5-P0）
 *
 * W0-5.3 治理内容：
 *  1. 用户命名空间：key 格式 `saleDraft:u{userId}:order:{orderId}|new:{customerDeptId}`，
 *     共享 PC 上不同登录用户的草稿完全隔离（蓝图 §2「共享设备」）；无用户段的旧格式 key
 *     仅在 7 天宽限期内向现有用户迁移一次（采用一次性迁移标记），过期不迁移也不读取；
 *  2. 版本号：记录携带 `ver`（结构版本，当前 2，不匹配即丢弃）与 `rev`（每用户单草稿修订号，每次保存 +1）；
 *  3. 容量治理：单条草稿 >256KB 不落本地（后端仍尝试双写）；每用户本地草稿上限 20 条，超限按 savedAt 淘汰最旧；
 *  4. 过期清理：默认 7 天（兼顾长假恢复），listDrafts/saveDraft 时惰性清扫过期与损坏记录；
 *  5. 恢复校验：结构校验（ver/rev/savedAt/details 数组/字段类型），损坏或非法记录返回 null 并清除。
 */
import useUserStore from '@/store/modules/user'
import { getSaleOrderDraft, saveSaleOrderDraft, removeSaleOrderDraft } from '@/api/order/saleDraft'

const KEY_PREFIX = 'saleDraft:'
export const SCHEMA_VER = 2
const MIGRATION_FLAG = 'saleDraft:migrated:legacy'
/** 过期时间（默认 7 天，可 setDraftMaxAge 调整）；后端草稿同口径 */
const DEFAULT_MAX_AGE = 7 * 24 * 60 * 60 * 1000
const MAX_AGE = DEFAULT_MAX_AGE
/** 每用户本地草稿条数上限 */
const MAX_DRAFTS = 20
/** 单条草稿本地存储体积上限（序列化后字节数） */
const MAX_DRAFT_BYTES = 256 * 1024

/* ========== 用户命名空间 ========== */

function currentUserId() {
  try {
    const s = useUserStore()
    return s.id || s.name || 'anon'
  } catch (e) {
    return 'anon'
  }
}

function userPrefix() {
  return `${KEY_PREFIX}u${currentUserId()}:`
}

/** 组合草稿 key（导出供页面使用，禁止手拼字符串） */
export function draftKeyOf(orderId, customerDeptId) {
  const id = orderId ? `order:${orderId}` : `new:${customerDeptId || 'nodept'}`
  return userPrefix() + id
}

/** 一次性迁移：把旧版无用户段的 key 归入当前登录用户（7 天宽限期内执行一次） */
function migrateLegacyDrafts() {
  try {
    if (localStorage.getItem(MIGRATION_FLAG) === '1') return
    const uid = currentUserId()
    const now = Date.now()
    const picked = []
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (!key || !key.startsWith(KEY_PREFIX) || /saleDraft:u[^:]+:/.test(key)) continue
      const raw = localStorage.getItem(key)
      try {
        const d = JSON.parse(raw)
        // 仅迁移 24h 内仍新鲜的旧草稿（旧版过期 24h）
        if (d && d.savedAt && now - new Date(d.savedAt).getTime() <= 24 * 3600 * 1000) {
          picked.push([key, raw])
        }
      } catch (e) { /* 损坏旧数据直接忽略 */ }
    }
    picked.forEach(([oldKey, raw]) => {
      try {
        const d = JSON.parse(raw)
        localStorage.setItem(`${KEY_PREFIX}u${uid}:${oldKey.slice(KEY_PREFIX.length)}`, JSON.stringify({ ...d, ver: SCHEMA_VER }))
        localStorage.removeItem(oldKey)
      } catch (e) { /* 忽略单条失败 */ }
    })
    localStorage.setItem(MIGRATION_FLAG, `${now}:${uid}`)
  } catch (e) {
    console.warn('[saleDraft] 旧草稿迁移失败', e)
  }
}

/* ========== 校验与解析 ========== */

/** 结构校验；返回 { ok, reason }。ver 不匹配/字段非法视为损坏 */
export function validateDraft(draft) {
  if (!draft || typeof draft !== 'object') return { ok: false, reason: 'empty' }
  if (draft.ver !== SCHEMA_VER) return { ok: false, reason: 'schema-ver' }
  if (!draft.savedAt || Number.isNaN(new Date(draft.savedAt).getTime())) return { ok: false, reason: 'saved-at' }
  if (draft.rev != null && typeof draft.rev !== 'number') return { ok: false, reason: 'rev' }
  if (!Array.isArray(draft.details)) return { ok: false, reason: 'details' }
  if (draft.orderId != null && typeof draft.orderId !== 'number' && typeof draft.orderId !== 'string') return { ok: false, reason: 'order-id' }
  if (draft.customerDeptId != null && typeof draft.customerDeptId !== 'number' && typeof draft.customerDeptId !== 'string') return { ok: false, reason: 'dept-id' }
  return { ok: true, reason: '' }
}

/** 解析并校验单条草稿（过期/损坏自动清除） */
function parseDraft(key, raw) {
  try {
    const draft = JSON.parse(raw)
    const v = validateDraft(draft)
    if (!v.ok) {
      localStorage.removeItem(key)
      return null
    }
    if (Date.now() - new Date(draft.savedAt).getTime() > MAX_AGE) {
      localStorage.removeItem(key)
      return null
    }
    return draft
  } catch (e) {
    localStorage.removeItem(key)
    return null
  }
}

/* ========== 容量治理 ========== */

/** 惰性清扫：删除当前用户过期/损坏记录，并把数量压到 MAX_DRAFTS 内（按 savedAt 淘汰最旧） */
function sweepAndPrune() {
  const prefix = userPrefix()
  const kept = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (!key || !key.startsWith(prefix)) continue
    const draft = parseDraft(key, localStorage.getItem(key))
    if (draft) kept.push({ key, savedAt: draft.savedAt })
  }
  if (kept.length > MAX_DRAFTS) {
    kept.sort((a, b) => new Date(b.savedAt) - new Date(a.savedAt))
    kept.slice(MAX_DRAFTS).forEach(({ key }) => localStorage.removeItem(key))
  }
}

/* ========== 本地草稿 API（保持原函数签名，detail.vue 无感升级） ========== */

/**
 * 列出当前用户所有未过期草稿（按保存时间倒序，附带清扫）
 * @returns {Array<{key:string, savedAt:string, rev:number, customerId, customerDeptId, deptName, orderCode, deliveryDate, remark, orderId, details:Array}>}
 */
export function listDrafts() {
  migrateLegacyDrafts()
  sweepAndPrune()
  const prefix = userPrefix()
  const result = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (!key || !key.startsWith(prefix)) continue
    const draft = parseDraft(key, localStorage.getItem(key))
    if (draft) result.push({ key, ...draft })
  }
  return result.sort((a, b) => new Date(b.savedAt) - new Date(a.savedAt))
}

/**
 * 保存草稿（同一 orderId/customerDeptId 覆盖旧草稿，rev 自增）
 * 单条超过 MAX_DRAFT_BYTES 时不落本地（返回 null，后端双写不受影响）
 */
export function saveDraft(payload) {
  const key = draftKeyOf(payload.orderId, payload.customerDeptId)
  let rev = 1
  try {
    const old = JSON.parse(localStorage.getItem(key) || 'null')
    if (old && typeof old.rev === 'number') rev = old.rev + 1
  } catch (e) { /* 旧记录损坏则 rev 从 1 起 */ }
  const draft = {
    ver: SCHEMA_VER,
    rev,
    key,
    savedAt: new Date().toISOString(),
    orderId: payload.orderId || null,
    customerId: payload.customerId || null,
    customerDeptId: payload.customerDeptId || null,
    deptName: payload.deptName || '',
    orderCode: payload.orderCode || null,
    // 班次（s35）：草稿需一并持久化，否则恢复后启用班次的客户会因缺班次而被拒/丢字段
    shiftCode: payload.shiftCode || null,
    // 载入时的服务端 updateTime（新单为 null）：草稿箱陈旧判定基线
    baseUpdateTime: payload.baseUpdateTime || null,
    deliveryDate: payload.deliveryDate || null,
    remark: payload.remark || null,
    details: payload.details || [],
  }
  const raw = JSON.stringify(draft)
  if (raw.length > MAX_DRAFT_BYTES) {
    console.warn('[saleDraft] 草稿超出本地容量上限，仅同步后端', key, raw.length)
    return null
  }
  localStorage.setItem(key, raw)
  sweepAndPrune()
  return draft
}

/** 删除草稿（仅接受当前用户命名空间内的 key） */
export function removeDraft(key) {
  if (key) localStorage.removeItem(key)
}

/** 按 key 读取草稿（不存在/过期/非法/非当前用户命名空间返回 null） */
export function restoreDraft(key) {
  if (!key || !key.startsWith(userPrefix())) return null
  const raw = localStorage.getItem(key)
  if (!raw) return null
  const draft = parseDraft(key, raw)
  return draft ? { key, ...draft } : null
}

/* ========== 后端草稿双写（录单页交互细化设计 §3.3：后端为主、本地兜底断网） ========== */

/** 草稿写入后端（fire-and-forget，失败静默由 localStorage 兜底）；rev 随本地记录带出供冲突比对 */
export function syncDraftToServer(payload) {
  try {
    const key = draftKeyOf(payload.orderId, payload.customerDeptId)
    let rev = null
    try {
      const local = JSON.parse(localStorage.getItem(key) || 'null')
      if (local && typeof local.rev === 'number') rev = local.rev
    } catch (e) { /* 无本地记录则不带 rev */ }
    return saveSaleOrderDraft({
      draftKey: key,
      payload: JSON.stringify({ ver: SCHEMA_VER, rev, ...payload }),
    }).catch(() => null)
  } catch (e) {
    return Promise.resolve(null)
  }
}

/** 从后端读取草稿载荷；不存在/过期/非法返回 null（口径与本地一致） */
export async function fetchDraftFromServer(orderId, customerDeptId) {
  try {
    const res = await getSaleOrderDraft(draftKeyOf(orderId, customerDeptId))
    const payloadStr = res.data && res.data.payload
    if (!payloadStr) return null
    const draft = JSON.parse(payloadStr)
    const v = validateDraft(draft)
    if (!v.ok) return null
    if (Date.now() - new Date(draft.savedAt).getTime() > MAX_AGE) {
      removeDraftFromServer(orderId, customerDeptId)
      return null
    }
    return draft
  } catch (e) {
    return null
  }
}

/** 删除后端草稿（提交成功/确认放弃时调用） */
export function removeDraftFromServer(orderId, customerDeptId) {
  try {
    return removeSaleOrderDraft(draftKeyOf(orderId, customerDeptId)).catch(() => null)
  } catch (e) {
    return Promise.resolve(null)
  }
}

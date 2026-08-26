/**
 * 销售订单草稿自动保存工具（localStorage，24h 过期）
 * 设计：docs/交互优化开发计划.md Phase 1.1
 *  - key: saleDraft:order:{orderId} | saleDraft:new:{customerDeptId}
 *  - 过期：保存时间超过 24h 视为失效，读取时自动清理
 */
const KEY_PREFIX = 'saleDraft:'
const MAX_AGE = 24 * 60 * 60 * 1000 // 24 小时

import { getSaleOrderDraft, saveSaleOrderDraft, removeSaleOrderDraft } from '@/api/order/saleDraft'

function draftKey(orderId, customerDeptId) {
  const id = orderId ? `order:${orderId}` : `new:${customerDeptId || 'nodept'}`
  return KEY_PREFIX + id
}

/** 解析并校验单条草稿（过期/损坏自动清除） */
function parseDraft(key, raw) {
  try {
    const draft = JSON.parse(raw)
    if (!draft || !draft.savedAt) return null
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

/**
 * 列出所有未过期草稿（按保存时间倒序）
 * @returns {Array<{key:string, savedAt:string, customerId, customerDeptId, deptName, orderCode, deliveryDate, remark, orderId, details:Array}>}
 */
export function listDrafts() {
  const result = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (!key || !key.startsWith(KEY_PREFIX)) continue
    const draft = parseDraft(key, localStorage.getItem(key))
    if (draft) result.push({ key, ...draft })
  }
  return result.sort((a, b) => new Date(b.savedAt) - new Date(a.savedAt))
}

/**
 * 保存草稿（同一 orderId/customerDeptId 覆盖旧草稿）
 * @returns 保存后的草稿（含 key / savedAt）
 */
export function saveDraft(payload) {
  const key = draftKey(payload.orderId, payload.customerDeptId)
  const draft = {
    key,
    savedAt: new Date().toISOString(),
    orderId: payload.orderId || null,
    customerId: payload.customerId || null,
    customerDeptId: payload.customerDeptId || null,
    deptName: payload.deptName || '',
    orderCode: payload.orderCode || null,
    deliveryDate: payload.deliveryDate || null,
    remark: payload.remark || null,
    details: payload.details || [],
  }
  localStorage.setItem(key, JSON.stringify(draft))
  return draft
}

/** 删除草稿 */
export function removeDraft(key) {
  if (key) localStorage.removeItem(key)
}

/** 按 key 读取草稿（不存在/过期返回 null，过期自动清除） */
export function restoreDraft(key) {
  if (!key) return null
  const raw = localStorage.getItem(key)
  if (!raw) return null
  const draft = parseDraft(key, raw)
  return draft ? { key, ...draft } : null
}

/* ========== 后端草稿双写（录单页交互细化设计 §3.3：后端为主、本地兜底断网） ========== */

/** 草稿写入后端（fire-and-forget，失败静默由 localStorage 兜底） */
export function syncDraftToServer(payload) {
  try {
    return saveSaleOrderDraft({
      draftKey: draftKey(payload.orderId, payload.customerDeptId),
      payload: JSON.stringify(payload),
    }).catch(() => null)
  } catch (e) {
    return Promise.resolve(null)
  }
}

/** 从后端读取草稿载荷；不存在返回 null */
export async function fetchDraftFromServer(orderId, customerDeptId) {
  try {
    const res = await getSaleOrderDraft(draftKey(orderId, customerDeptId))
    const payloadStr = res.data && res.data.payload
    if (!payloadStr) return null
    const draft = JSON.parse(payloadStr)
    if (!draft || !draft.savedAt) return null
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
    return removeSaleOrderDraft(draftKey(orderId, customerDeptId)).catch(() => null)
  } catch (e) {
    return Promise.resolve(null)
  }
}

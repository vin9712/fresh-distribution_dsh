import request from '@/utils/request'

// 查询采购单列表
export function listPurchase(query) {
  return request({
    url: '/purchase/list',
    method: 'get',
    params: query
  })
}

// 查询采购单详细
export function getPurchase(id) {
  return request({
    url: '/purchase/' + id,
    method: 'get'
  })
}

// 查询采购单批次明细列表
export function getPurchaseItems(id) {
  return request({
    url: '/purchase/' + id + '/items',
    method: 'get'
  })
}

// 当日应采汇总（D-056：应采清单实时视图 + 已录批次）
export function daySummary(orderDate) {
  return request({
    url: '/purchase/day-summary',
    method: 'get',
    params: { orderDate }
  })
}

// 取或惰性创建当日采购单（有写副作用，POST）
export function dayOrder(orderDate) {
  return request({
    url: '/purchase/day-order',
    method: 'post',
    params: { orderDate }
  })
}

// 追加采购批次（一次实际进货）
export function addBatch(id, data) {
  return request({
    url: '/purchase/' + id + '/batch',
    method: 'post',
    data: data
  })
}

// 批量追加采购批次（任一行非法整体回滚）
export function addBatchBulk(id, data) {
  return request({
    url: '/purchase/' + id + '/batch-bulk',
    method: 'post',
    data: data
  })
}

// 修改采购批次（仅草稿）
export function updateBatch(id, itemId, data) {
  return request({
    url: '/purchase/' + id + '/batch/' + itemId,
    method: 'put',
    data: data
  })
}

// 删除采购批次（仅草稿）
export function delBatch(id, itemId) {
  return request({
    url: '/purchase/' + id + '/batch/' + itemId,
    method: 'delete'
  })
}

// 修改采购单单头（默认供应商/采购员/备注）
export function updatePurchaseHeader(data) {
  return request({
    url: '/purchase',
    method: 'put',
    data: data
  })
}

// 确认采购单（草稿→已确认，锁定批次增删）
export function confirmPurchase(id) {
  return request({
    url: '/purchase/' + id + '/confirm',
    method: 'put'
  })
}

// 采购单入库
export function stockInPurchase(id) {
  return request({
    url: '/purchase/' + id + '/stockIn',
    method: 'put'
  })
}

// 批量入库（S2-2.2 批量确认成本）：仅已确认采购单
export function batchStockInPurchase(ids) {
  return request({
    url: '/purchase/batch-stock-in',
    method: 'put',
    data: ids
  })
}

// 已确认采购单调整数量/成本（W0-2.5，带前后金额审计）
export function adjustPurchase(id, data) {
  return request({
    url: '/purchase/' + id + '/adjust',
    method: 'put',
    data: data
  })
}

// 采购单调整审计日志（W0-2.5）
export function getModifyLogs(id) {
  return request({
    url: '/purchase/' + id + '/modify-logs',
    method: 'get'
  })
}

// 供应商补录（S2-2.2）：草稿/已确认采购单补录供应商与采购员
export function backfillSupplier(id, data) {
  return request({
    url: '/purchase/' + id + '/supplier',
    method: 'put',
    data: data
  })
}

// 删除采购单
export function delPurchase(id) {
  return request({
    url: '/purchase/' + id,
    method: 'delete'
  })
}

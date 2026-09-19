import request from '@/utils/request'

// 分页查询销售订单列表
export function pageSaleOrder(query) {
  return request({
    url: '/order/sale/page',
    method: 'get',
    params: query
  })
}

// 客户视角分组聚合分页（D-064：一行 = 客户 + 配送日期，分页单位 = 客户行）
export function pageSaleCustomer(query) {
  return request({
    url: '/order/sale/customer-page',
    method: 'get',
    params: query
  })
}

// 查询销售订单列表（客户视角子行 / 明细视角共用）
export function listSale(query) {
  return request({
    url: '/order/sale/list',
    method: 'get',
    params: query
  })
}

// 查询最近销售订单列表
export function recentSaleOrder(query) {
  return request({
    url: '/order/sale/recent/list',
    method: 'get',
    params: query
  })
}

// 按订单编号批量查询简报（草稿箱陈旧判定：编号已在服务端保存/推进 → 本地草稿为旧数据）
export function getOrderBriefByCodes(codes) {
  return request({
    url: '/order/sale/by-codes',
    method: 'get',
    params: { codes: (codes || []).join(',') }
  })
}

// 查询销售订单详细
export function getSaleOrder(id) {
  return request({
    url: '/order/sale/' + id,
    method: 'get'
  })
}

// 新增销售订单
export function addSale(data) {
  return request({
    url: '/order/sale',
    method: 'post',
    data: data
  })
}


// 新增销售订单+明细
export function createSaleOrder(data) {
  return request({
    url: '/order/sale/create',
    method: 'post',
    data: data
  })
}

// 修改销售订单+明细
export function updateSaleOrder(data) {
  return request({
    url: '/order/sale/update',
    method: 'put',
    data: data
  })
}

// 修改销售订单
export function updateSale(data) {
  return request({
    url: '/order/sale',
    method: 'put',
    data: data
  })
}

// 修改销售订单状态
export function updateOrderStatus(data) {
  return request({
    url: '/order/sale/status',
    method: 'put',
    data: data
  })
}

// 删除销售订单
export function delSale(id) {
  return request({
    url: '/order/sale/' + id,
    method: 'delete'
  })
}

// 获取或生成销售单号
export function genOrderCode(query) {
  return request({
    url: '/order/sale/code',
    method: 'get',
    params: query
  })
}

// 生成单据前汇总预览（列表页抽屉第一步，按品类分组）
export function generatePreview(data) {
  return request({
    url: '/order/sale/generatePreview',
    method: 'post',
    data: data
  })
}

// 检测同配送点+同配送日期的草稿订单（新增订单页选中客户后调用）
export function checkExistingDraft(query) {
  return request({
    url: '/order/sale/checkDraft',
    method: 'get',
    params: query
  })
}
// 配送后加单（D-055：原订单不变，标记附加订单明细）
export function deliverySupplement(orderId, data) {
  return request({
    url: '/order/delivery-change/' + orderId + '/supplement',
    method: 'post',
    data: data
  })
}

// 配送后换货（D-055：被换行标退货+新行标换货，同组）
export function deliveryExchange(orderId, data) {
  return request({
    url: '/order/delivery-change/' + orderId + '/exchange',
    method: 'post',
    data: data
  })
}

// 配送后退货（D-055：原行标退货，应送实收归0）
export function deliveryReturn(orderId, data) {
  return request({
    url: '/order/delivery-change/' + orderId + '/return',
    method: 'post',
    data: data
  })
}

// 配送后变更回退（OA：加单删行 / 退货按快照恢复 / 换货整组恢复；验收草稿自动同步）
export function revokeDeliveryChange(orderId, detailId) {
  return request({
    url: '/order/delivery-change/' + orderId + '/revoke/' + detailId,
    method: 'post'
  })
}

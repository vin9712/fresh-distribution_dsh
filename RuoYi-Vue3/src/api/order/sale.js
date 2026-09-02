import request from '@/utils/request'

// 分页查询销售订单列表
export function pageSaleOrder(query) {
  return request({
    url: '/order/sale/page',
    method: 'get',
    params: query
  })
}

// 查询销售订单列表
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

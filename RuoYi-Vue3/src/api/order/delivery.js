import request from '@/utils/request'

// 分页查询送货单列表
export function pageDelivery(query) {
  return request({
    url: '/order/delivery/page',
    method: 'get',
    params: query
  })
}

// 查询送货单列表
export function listDelivery(query) {
  return request({
    url: '/order/delivery/list',
    method: 'get',
    params: query
  })
}

// 查询送货单详细
export function getDelivery(id) {
  return request({
    url: '/order/delivery/' + id,
    method: 'get'
  })
}

// 查询送货单明细列表（按商品合并行）
export function listDeliveryDetail(deliveryId) {
  return request({
    url: '/order/delivery/' + deliveryId + '/detail',
    method: 'get'
  })
}

// 按配送日期生成送货单（仅汇总已确认订单）
export function generateDelivery(deliveryDate) {
  return request({
    url: '/order/delivery/generate/' + deliveryDate,
    method: 'post'
  })
}

// 标记打印（print_count + 1）
export function printDelivery(id) {
  return request({
    url: '/order/delivery/' + id + '/print',
    method: 'put'
  })
}

// 标记送达（同组已确认订单 → DELIVERED）
export function deliverDelivery(id) {
  return request({
    url: '/order/delivery/' + id + '/deliver',
    method: 'put'
  })
}

// 新增送货单
export function addDelivery(data) {
  return request({
    url: '/order/delivery',
    method: 'post',
    data: data
  })
}

// 修改送货单
export function updateDelivery(data) {
  return request({
    url: '/order/delivery',
    method: 'put',
    data: data
  })
}

// 删除送货单
export function delDelivery(id) {
  return request({
    url: '/order/delivery/' + id,
    method: 'delete'
  })
}

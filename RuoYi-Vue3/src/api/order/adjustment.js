import request from '@/utils/request'

// 创建订单调整（配送后加退换）
export function createAdjustment(data) {
  return request({
    url: '/order/adjustment',
    method: 'post',
    data: data
  })
}

// 按订单ID查询调整列表
export function listAdjustment(orderId) {
  return request({
    url: '/order/adjustment/order/' + orderId,
    method: 'get'
  })
}

// 查询调整详情
export function getAdjustment(id) {
  return request({
    url: '/order/adjustment/' + id,
    method: 'get'
  })
}

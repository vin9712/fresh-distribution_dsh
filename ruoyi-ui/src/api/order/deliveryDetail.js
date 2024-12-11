import request from '@/utils/request'

// 分页查询送货单详情列表
export function pageDelivery(query) {
  return request({
    url: '/order/delivery/page',
    method: 'get',
    params: query
  })
}

// 查询送货单详情列表
export function listDelivery(query) {
  return request({
    url: '/order/delivery/list',
    method: 'get',
    params: query
  })
}

// 查询送货单详情详细
export function getDelivery(id) {
  return request({
    url: '/order/delivery/' + id,
    method: 'get'
  })
}

// 新增送货单详情
export function addDelivery(data) {
  return request({
    url: '/order/delivery',
    method: 'post',
    data: data
  })
}

// 修改送货单详情
export function updateDelivery(data) {
  return request({
    url: '/order/delivery',
    method: 'put',
    data: data
  })
}

// 删除送货单详情
export function delDelivery(id) {
  return request({
    url: '/order/delivery/' + id,
    method: 'delete'
  })
}

import request from '@/utils/request'

// 分页查询销售订单列表
export function pageSale(query) {
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

// 查询销售订单详细
export function getSale(id) {
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

// 修改销售订单
export function updateSale(data) {
  return request({
    url: '/order/sale',
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

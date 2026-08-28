import request from '@/utils/request'

// 分页查询销售订单详情列表
export function pageSaleDetail(query) {
  return request({
    url: '/order/saleDetail/page',
    method: 'get',
    params: query
  })
}

// 查询销售订单详情列表
export function listSaleDetail(query) {
  return request({
    url: '/order/saleDetail/list',
    method: 'get',
    params: query
  })
}

// 查询销售订单详情详细
// 常用商品统计：近N天下单频率最高的 SKU（录单页"常用"面板）
export function frequentSaleDetail(query) {
  return request({
    url: '/order/saleDetail/frequent',
    method: 'get',
    params: query
  })
}

// 查询销售订单详情详细
export function getSaleDetail(id) {
  return request({
    url: '/order/saleDetail/' + id,
    method: 'get'
  })
}

// 新增销售订单详情
export function addSaleDetail(data) {
  return request({
    url: '/order/saleDetail',
    method: 'post',
    data: data
  })
}

// 修改销售订单详情
export function updateSaleDetail(data) {
  return request({
    url: '/order/saleDetail',
    method: 'put',
    data: data
  })
}

// 删除销售订单详情
export function delSaleDetail(id) {
  return request({
    url: '/order/saleDetail/' + id,
    method: 'delete'
  })
}
// 说明：订单页实收/验收三接口（/order/saleDetail/actual/draft|accept|revoke）
// 已随 S14/T5 下线（C1：实收归验收单），前端封装一并移除。

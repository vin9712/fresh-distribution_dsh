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

/* ========== 订单页实收与验收（验收回归订单本体） ========== */

// 批量保存实收草稿（明细区实收列防抖自动保存）
export function saveActualDraft(data) {
  return request({
    url: '/order/saleDetail/actual/draft',
    method: 'post',
    data: data
  })
}

// 批量确认验收（±20% 差异仅提示非阻断，空实收行按下单数计）
export function acceptOrders(data) {
  return request({
    url: '/order/saleDetail/actual/accept',
    method: 'post',
    data: data
  })
}

// 撤销验收（已验收回退已配送并清空实收数据；已结算禁止）
export function revokeAcceptance(data) {
  return request({
    url: '/order/saleDetail/actual/revoke',
    method: 'post',
    data: data
  })
}

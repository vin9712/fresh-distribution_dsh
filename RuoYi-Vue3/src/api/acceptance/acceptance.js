import request from '@/utils/request'

// 分页查询验收单列表
export function pageAcceptance(query) {
  return request({
    url: '/acceptance/page',
    method: 'get',
    params: query
  })
}

// 查询验收单列表
export function listAcceptance(query) {
  return request({
    url: '/acceptance/list',
    method: 'get',
    params: query
  })
}

// 查询验收单详细
export function getAcceptance(id) {
  return request({
    url: '/acceptance/' + id,
    method: 'get'
  })
}

// 查询验收单明细列表
export function listAcceptanceItems(id) {
  return request({
    url: '/acceptance/' + id + '/items',
    method: 'get'
  })
}

// 按送货单生成验收单（一单一验）
export function createAcceptance(data) {
  return request({
    url: '/acceptance',
    method: 'post',
    data: data
  })
}

// AC-1：按 客户+配送日期 生成验收单（一客户日一验；应送行=该客户当日全部订单明细行，跨点平铺含标记）
export function createAcceptanceByCustomerDate(data) {
  return request({
    url: '/acceptance/create-by-customer-date',
    method: 'post',
    data: data
  })
}

// 录入/修改验收单（仅草稿，后端重算金额与损耗）
export function updateAcceptance(data) {
  return request({
    url: '/acceptance',
    method: 'put',
    data: data
  })
}

// 提交验收单（同组订单 → ACCEPTED）
export function submitAcceptance(id) {
  return request({
    url: '/acceptance/' + id + '/submit',
    method: 'put'
  })
}

// 「去验收」定位：OA 订单维度优先（orderView=true，订单页验收模式），历史单回退送货单反查
export function locateAcceptanceByOrder(orderId) {
  return request({
    url: '/acceptance/by-order/' + orderId,
    method: 'get'
  })
}

// 撤销验收（S14/T5：已提交→草稿，原因必填，来源订单回退已配送）
export function revokeAcceptance(id, reason) {
  return request({
    url: '/acceptance/' + id + '/revoke',
    method: 'post',
    data: { reason: reason }
  })
}

// 删除验收单（仅草稿）
export function delAcceptance(id) {
  return request({
    url: '/acceptance/' + id,
    method: 'delete'
  })
}

// ======== OA：订单维度验收（《订单页一键验收链路设计》） ========

// OA：按订单生成（或同步）验收草稿（一订单一验；已有草稿幂等同步缺失行）
export function createAcceptanceByOrder(orderId) {
  return request({
    url: '/acceptance/create-by-order',
    method: 'post',
    data: { orderId: orderId }
  })
}

// OA：订单一键验收（建单如无 → 同步缺失行 → 实收覆盖可选 → 提交，原子完成）
// items 键 = saleOrderDetailId；不传 items 即全部实收=下单数量
export function quickAcceptOrder(data) {
  return request({
    url: '/acceptance/quick-accept',
    method: 'post',
    data: data
  })
}

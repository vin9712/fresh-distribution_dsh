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

// 「去验收」定位：按来源订单反查所在有效送货单与验收单
// （有验收单→跳转验收单；无→带 deliveryId 引导创建草稿）
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

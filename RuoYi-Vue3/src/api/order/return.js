import request from '@/utils/request'

// 分页查询退货单列表（支持 settleScope 过滤）
export function pageReturnOrder(query) {
  return request({
    url: '/order/return/page',
    method: 'get',
    params: query
  })
}

// 查询退货单列表
export function listReturnOrder(query) {
  return request({
    url: '/order/return/list',
    method: 'get',
    params: query
  })
}

// 查询退货单详情
export function getReturnOrder(id) {
  return request({
    url: '/order/return/' + id,
    method: 'get'
  })
}

// 查询退货单明细
export function listReturnItems(id) {
  return request({
    url: '/order/return/' + id + '/items',
    method: 'get'
  })
}

// 新增退货单草稿（数量上限/单价锁由后端校验）
export function addReturnOrder(data) {
  return request({
    url: '/order/return',
    method: 'post',
    data: data
  })
}

// 修改退货单草稿
export function updateReturnOrder(data) {
  return request({
    url: '/order/return',
    method: 'put',
    data: data
  })
}

// 提交退货单（草稿→已提交质检中，settle_scope 快照）
export function submitReturnOrder(id) {
  return request({
    url: '/order/return/' + id + '/submit',
    method: 'put'
  })
}

// 退货单质检（已提交→质检完成，逐行记质检结论）
export function inspectReturnOrder(id, items) {
  return request({
    url: '/order/return/' + id + '/inspect',
    method: 'post',
    data: { items: items }
  })
}

// 删除退货单（仅草稿，逻辑删除）
export function delReturnOrder(id) {
  return request({
    url: '/order/return/' + id,
    method: 'delete'
  })
}

import request from '@/utils/request'

// 查询采购单列表
export function listPurchase(query) {
  return request({
    url: '/purchase/list',
    method: 'get',
    params: query
  })
}

// 查询采购单详细
export function getPurchase(id) {
  return request({
    url: '/purchase/' + id,
    method: 'get'
  })
}

// 查询采购单明细列表
export function getPurchaseItems(id) {
  return request({
    url: '/purchase/' + id + '/items',
    method: 'get'
  })
}

// 按配送日期自动生成采购单
export function generatePurchase(data) {
  return request({
    url: '/purchase/generate',
    method: 'post',
    data: data
  })
}

// 按勾选订单生成采购单（销售订单列表页抽屉）
export function generatePurchaseByOrders(data) {
  return request({
    url: '/purchase/generate-by-orders',
    method: 'post',
    data: data
  })
}

// 手工新增采购单
export function addPurchase(data) {
  return request({
    url: '/purchase',
    method: 'post',
    data: data
  })
}

// 修改采购单
export function updatePurchase(data) {
  return request({
    url: '/purchase',
    method: 'put',
    data: data
  })
}

// 确认采购单
export function confirmPurchase(id) {
  return request({
    url: '/purchase/' + id + '/confirm',
    method: 'put'
  })
}

// 采购单入库
export function stockInPurchase(id) {
  return request({
    url: '/purchase/' + id + '/stockIn',
    method: 'put'
  })
}

// 删除采购单
export function delPurchase(id) {
  return request({
    url: '/purchase/' + id,
    method: 'delete'
  })
}

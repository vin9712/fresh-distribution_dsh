import request from '@/utils/request'

// 分页查询商品信息列表
export function pageSku(query) {
  return request({
    url: '/product/sku/page',
    method: 'get',
    params: query
  })
}

// 查询商品信息列表
export function listSku(query) {
  return request({
    url: '/product/sku/list',
    method: 'get',
    params: query
  })
}

// 查询商品信息详细
export function getSku(id) {
  return request({
    url: '/product/sku/' + id,
    method: 'get'
  })
}

// 新增商品信息
export function addSku(data) {
  return request({
    url: '/product/sku',
    method: 'post',
    data: data
  })
}

// 修改商品信息
export function updateSku(data) {
  return request({
    url: '/product/sku',
    method: 'put',
    data: data
  })
}

// 批量匹配商品信息
export function matchSku(data) {
  return request({
    url: '/product/sku/match',
    method: 'put',
    data: data
  })
}

// 批量取消匹配商品信息
export function undoMatchSku(data) {
  return request({
    url: '/product/sku/undoMatch',
    method: 'put',
    data: data
  })
}

// 删除商品信息
export function delSku(id) {
  return request({
    url: '/product/sku/' + id,
    method: 'delete'
  })
}

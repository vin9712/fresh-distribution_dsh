import request from '@/utils/request'

// 查询商品全局别名列表
export function listAlias(query) {
  return request({
    url: '/product/alias/list',
    method: 'get',
    params: query
  })
}

// 查询商品全局别名详细
export function getAlias(id) {
  return request({
    url: '/product/alias/' + id,
    method: 'get'
  })
}

// 按关键词检索别名
export function listAliasByKeyword(keyword) {
  return request({
    url: '/product/alias/keyword/' + keyword,
    method: 'get'
  })
}

// 新增商品全局别名
export function addAlias(data) {
  return request({
    url: '/product/alias',
    method: 'post',
    data: data
  })
}

// 修改商品全局别名
export function updateAlias(data) {
  return request({
    url: '/product/alias',
    method: 'put',
    data: data
  })
}

// 删除商品全局别名
export function delAlias(id) {
  return request({
    url: '/product/alias/' + id,
    method: 'delete'
  })
}

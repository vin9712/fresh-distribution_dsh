import request from '@/utils/request'

// 查询客户SKU映射列表
export function listMapping(query) {
  return request({
    url: '/product/mapping/list',
    method: 'get',
    params: query
  })
}

// 查询客户SKU映射详细
export function getMapping(id) {
  return request({
    url: '/product/mapping/' + id,
    method: 'get'
  })
}

// 新增客户SKU映射
export function addMapping(data) {
  return request({
    url: '/product/mapping',
    method: 'post',
    data: data
  })
}

// 修改客户SKU映射
export function updateMapping(data) {
  return request({
    url: '/product/mapping',
    method: 'put',
    data: data
  })
}

// 删除客户SKU映射
export function delMapping(id) {
  return request({
    url: '/product/mapping/' + id,
    method: 'delete'
  })
}

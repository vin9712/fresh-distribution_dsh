import request from '@/utils/request'

// 查询临时商品列表
export function listTemp(query) {
  return request({
    url: '/product/temp/list',
    method: 'get',
    params: query
  })
}

// 查询临时商品详细
export function getTemp(id) {
  return request({
    url: '/product/temp/' + id,
    method: 'get'
  })
}

// 新增临时商品
export function addTemp(data) {
  return request({
    url: '/product/temp',
    method: 'post',
    data: data
  })
}

// 修改临时商品
export function updateTemp(data) {
  return request({
    url: '/product/temp',
    method: 'put',
    data: data
  })
}

// 删除临时商品
export function delTemp(id) {
  return request({
    url: '/product/temp/' + id,
    method: 'delete'
  })
}

// 临时商品转正为正式SKU
export function convertTemp(id, data) {
  return request({
    url: '/product/temp/' + id + '/convert',
    method: 'post',
    data: data
  })
}

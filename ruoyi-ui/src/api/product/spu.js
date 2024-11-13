import request from '@/utils/request'

// 分页查询商品spu列表
export function pageSpu(query) {
  return request({
    url: '/product/spu/page',
    method: 'get',
    params: query
  })
}

// 查询商品spu列表
export function listSpu(query) {
  return request({
    url: '/product/spu/list',
    method: 'get',
    params: query
  })
}

// 查询商品spu详细
export function getSpu(id) {
  return request({
    url: '/product/spu/' + id,
    method: 'get'
  })
}

// 新增商品spu
export function addSpu(data) {
  return request({
    url: '/product/spu',
    method: 'post',
    data: data
  })
}

// 修改商品spu
export function updateSpu(data) {
  return request({
    url: '/product/spu',
    method: 'put',
    data: data
  })
}

// 删除商品spu
export function delSpu(id) {
  return request({
    url: '/product/spu/' + id,
    method: 'delete'
  })
}
import request from '@/utils/request'

// 查询配送点报价列表
export function listPointPrice(query) {
  return request({
    url: '/price/point/list',
    method: 'get',
    params: query
  })
}

// 查询配送点报价详细
export function getPointPrice(id) {
  return request({
    url: '/price/point/' + id,
    method: 'get'
  })
}

// 新增配送点报价
export function addPointPrice(data) {
  return request({
    url: '/price/point',
    method: 'post',
    data: data
  })
}

// 修改配送点报价
export function updatePointPrice(data) {
  return request({
    url: '/price/point',
    method: 'put',
    data: data
  })
}

// 删除配送点报价
export function delPointPrice(id) {
  return request({
    url: '/price/point/' + id,
    method: 'delete'
  })
}

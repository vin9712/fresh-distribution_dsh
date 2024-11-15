import request from '@/utils/request'

// 分页查询商品报价明细列表
export function pageQuoteDetail(query) {
  return request({
    url: '/quote/quoteDetail/page',
    method: 'get',
    params: query
  })
}

// 查询商品报价明细列表
export function listQuoteDetail(query) {
  return request({
    url: '/quote/quoteDetail/list',
    method: 'get',
    params: query
  })
}

// 查询商品报价明细详细
export function getQuoteDetail(id) {
  return request({
    url: '/quote/quoteDetail/' + id,
    method: 'get'
  })
}

// 新增商品报价明细
export function addQuoteDetail(data) {
  return request({
    url: '/quote/quoteDetail',
    method: 'post',
    data: data
  })
}

// 修改商品报价明细
export function updateQuoteDetail(data) {
  return request({
    url: '/quote/quoteDetail',
    method: 'put',
    data: data
  })
}

// 删除商品报价明细
export function delQuoteDetail(id) {
  return request({
    url: '/quote/quoteDetail/' + id,
    method: 'delete'
  })
}

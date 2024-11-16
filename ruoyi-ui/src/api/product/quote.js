import request from '@/utils/request'

// 分页查询商品报价列表
export function pageQuote(query) {
  return request({
    url: '/product/quote/page',
    method: 'get',
    params: query
  })
}

// 查询商品报价列表
export function listQuote(query) {
  return request({
    url: '/product/quote/list',
    method: 'get',
    params: query
  })
}

// 获取或生成商品报价单号
export function genQuoteCode(query) {
  return request({
    url: '/product/quote/code',
    method: 'get',
    params: query
  })
}

// 查询商品报价详细
export function getQuote(id) {
  return request({
    url: '/product/quote/' + id,
    method: 'get'
  })
}

// 新增商品报价
export function addQuote(data) {
  return request({
    url: '/product/quote',
    method: 'post',
    data: data
  })
}

// 修改商品报价
export function updateQuote(data) {
  return request({
    url: '/product/quote',
    method: 'put',
    data: data
  })
}

// 删除商品报价
export function delQuote(id) {
  return request({
    url: '/product/quote/' + id,
    method: 'delete'
  })
}

import request from '@/utils/request'

// 三层取价：配送点报价 > 客户报价 > 客户模板
export function queryPrice(params) {
  return request({
    url: '/price/query',
    method: 'get',
    params
  })
}

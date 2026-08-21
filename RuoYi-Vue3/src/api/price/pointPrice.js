import request from '@/utils/request'

// 查询配送点商品覆盖列表
export function listDeliverySkuOverride(query) {
  return request({
    url: '/price/delivery-override/list',
    method: 'get',
    params: query
  })
}

// 查询配送点商品覆盖详细
export function getDeliverySkuOverride(id) {
  return request({
    url: '/price/delivery-override/' + id,
    method: 'get'
  })
}

// 新增/更新配送点商品覆盖（同配送点+SKU 存在则更新）
export function saveDeliverySkuOverride(data) {
  return request({
    url: '/price/delivery-override',
    method: 'post',
    data: data
  })
}

// 删除配送点商品覆盖
export function delDeliverySkuOverride(ids) {
  return request({
    url: '/price/delivery-override/' + ids,
    method: 'delete'
  })
}

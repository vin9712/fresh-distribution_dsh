import request from '@/utils/request'

// 分页查询客户商品列表
export function pageCustomerSku(query) {
  return request({
    url: '/product/customer-sku/page',
    method: 'get',
    params: query
  })
}

// 客户商品池列表（管理页用，不合并配送点覆盖）
export function listCustomerSkuPool(query) {
  return request({
    url: '/product/customer-sku/pool',
    method: 'get',
    params: query
  })
}

// 客户商品列表（合并配送点覆盖，供录单选择商品）
export function listCustomerSku(query) {
  return request({
    url: '/product/customer-sku/list',
    method: 'get',
    params: query
  })
}

// 查询客户商品详细
export function getCustomerSku(id) {
  return request({
    url: '/product/customer-sku/' + id,
    method: 'get'
  })
}

// 新增客户商品（单个指派）
export function addCustomerSku(data) {
  return request({
    url: '/product/customer-sku',
    method: 'post',
    data: data
  })
}

// 个性化修改（后端自动置 is_follow_default=0）
export function updateCustomerSku(data) {
  return request({
    url: '/product/customer-sku',
    method: 'put',
    data: data
  })
}

// 批量赋值默认SKU（body: { skuIds, customerIds, strategy, templateId }）
export function assignCustomerSku(data) {
  return request({
    url: '/product/customer-sku/assign',
    method: 'post',
    data: data
  })
}

// 删除客户商品
export function delCustomerSku(ids) {
  return request({
    url: '/product/customer-sku/' + ids,
    method: 'delete'
  })
}

// ============ 默认SKU模板（批量赋值用） ============

// 查询模板列表
export function listDefaultSkuTemplate(query) {
  return request({
    url: '/product/default-sku-template/list',
    method: 'get',
    params: query
  })
}

// 查询模板详细（含 skuIds）
export function getDefaultSkuTemplate(id) {
  return request({
    url: '/product/default-sku-template/' + id,
    method: 'get'
  })
}

// 查询模板明细（SKU集合）
export function listDefaultSkuTemplateItems(id) {
  return request({
    url: '/product/default-sku-template/' + id + '/items',
    method: 'get'
  })
}

// 新增模板（body: { name, customerGroupId, status, skuIds }）
export function addDefaultSkuTemplate(data) {
  return request({
    url: '/product/default-sku-template',
    method: 'post',
    data: data
  })
}

// 修改模板
export function updateDefaultSkuTemplate(data) {
  return request({
    url: '/product/default-sku-template',
    method: 'put',
    data: data
  })
}

// 删除模板
export function delDefaultSkuTemplate(ids) {
  return request({
    url: '/product/default-sku-template/' + ids,
    method: 'delete'
  })
}

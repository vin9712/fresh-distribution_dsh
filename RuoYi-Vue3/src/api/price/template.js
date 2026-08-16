import request from '@/utils/request'

// 查询报价模板列表
export function listTemplate(query) {
  return request({
    url: '/price/template/list',
    method: 'get',
    params: query
  })
}

// 查询报价模板详细
export function getTemplate(id) {
  return request({
    url: '/price/template/' + id,
    method: 'get'
  })
}

// 新增报价模板
export function addTemplate(data) {
  return request({
    url: '/price/template',
    method: 'post',
    data: data
  })
}

// 修改报价模板
export function updateTemplate(data) {
  return request({
    url: '/price/template',
    method: 'put',
    data: data
  })
}

// 删除报价模板
export function delTemplate(id) {
  return request({
    url: '/price/template/' + id,
    method: 'delete'
  })
}

// 查询模板SKU价格明细
export function listTemplateSkus(id) {
  return request({
    url: '/price/template/' + id + '/skus',
    method: 'get'
  })
}

// 新增模板SKU价格
export function addTemplateSku(id, data) {
  return request({
    url: '/price/template/' + id + '/skus',
    method: 'post',
    data: data
  })
}

// 修改模板SKU价格
export function updateTemplateSku(id, data) {
  return request({
    url: '/price/template/skus/' + id,
    method: 'put',
    data: data
  })
}

// 删除模板SKU价格
export function delTemplateSku(id) {
  return request({
    url: '/price/template/skus/' + id,
    method: 'delete'
  })
}

// 查询模板绑定客户
export function listTemplateCustomers(id) {
  return request({
    url: '/price/template/' + id + '/customers',
    method: 'get'
  })
}

// 绑定客户
export function bindCustomer(id, customerId) {
  return request({
    url: '/price/template/' + id + '/bind',
    method: 'post',
    params: { customerId: customerId }
  })
}

// 解绑客户
export function unbindCustomer(id, customerId) {
  return request({
    url: '/price/template/' + id + '/unbind',
    method: 'delete',
    params: { customerId: customerId }
  })
}

// 设为默认模板
export function setDefault(id) {
  return request({
    url: '/price/template/' + id + '/default',
    method: 'post'
  })
}

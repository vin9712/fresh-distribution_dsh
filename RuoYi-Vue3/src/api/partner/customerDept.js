import request from '@/utils/request'

// 分页查询客户部门列表
export function pageCustomerDept(query) {
  return request({
    url: '/partner/customerDept/page',
    method: 'get',
    params: query
  })
}

// 查询客户部门列表
export function listCustomerDept(query) {
  return request({
    url: '/partner/customerDept/list',
    method: 'get',
    params: query
  })
}

// 查询客户部门详细
export function getCustomerDept(id) {
  return request({
    url: '/partner/customerDept/' + id,
    method: 'get'
  })
}

// 新增客户部门
export function addCustomerDept(data) {
  return request({
    url: '/partner/customerDept',
    method: 'post',
    data: data
  })
}

// 修改客户部门
export function updateCustomerDept(data) {
  return request({
    url: '/partner/customerDept',
    method: 'put',
    data: data
  })
}

// 删除客户部门
export function delCustomerDept(id) {
  return request({
    url: '/partner/customerDept/' + id,
    method: 'delete'
  })
}

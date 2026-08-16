import request from '@/utils/request'

// 分页查询客户管理列表
export function pageCustomer(query) {
	return request({
		url: '/partner/customer/page',
		method: 'get',
		params: query
	})
}

// 查询客户管理列表
export function listCustomer(query) {
	return request({
		url: '/partner/customer/list',
		method: 'get',
		params: query
	})
}

// 查询客户详细
export function getCustomer(id) {
	return request({
		url: '/partner/customer/' + id,
		method: 'get'
	})
}

// 新增客户
export function addCustomer(data) {
	return request({
		url: '/partner/customer',
		method: 'post',
		data: data
	})
}

// 修改客户
export function updateCustomer(data) {
	return request({
		url: '/partner/customer',
		method: 'put',
		data: data
	})
}

// 删除客户
export function delCustomer(id) {
	return request({
		url: '/partner/customer/' + id,
		method: 'delete'
	})
}

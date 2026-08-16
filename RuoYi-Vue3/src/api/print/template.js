import request from '@/utils/request'

// 分页查询打印模板列表
export function pagePrintTemplate(query) {
  return request({
    url: '/print/template/page',
    method: 'get',
    params: query
  })
}

// 查询打印模板列表
export function listPrintTemplate(query) {
  return request({
    url: '/print/template/list',
    method: 'get',
    params: query
  })
}

// 查询打印模板详细
export function getPrintTemplate(id) {
  return request({
    url: '/print/template/' + id,
    method: 'get'
  })
}

// 新增打印模板
export function addPrintTemplate(data) {
  return request({
    url: '/print/template',
    method: 'post',
    data: data
  })
}

// 修改打印模板
export function updatePrintTemplate(data) {
  return request({
    url: '/print/template',
    method: 'put',
    data: data
  })
}

// 删除打印模板
export function delPrintTemplate(id) {
  return request({
    url: '/print/template/' + id,
    method: 'delete'
  })
}

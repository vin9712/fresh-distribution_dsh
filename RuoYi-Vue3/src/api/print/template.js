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

// 查询积木报表设计器可用报表清单（打印模板表单下拉选择用）
export function listJimuReports() {
  return request({
    url: '/print/template/jimu-reports',
    method: 'get'
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

// 测试发布（W0-4.4：置已测试 + 测试水印，测试打印不计正式次数）
export function testPublishPrintTemplate(id) {
  return request({
    url: '/print/template/' + id + '/test-publish',
    method: 'put'
  })
}

// 正式发布（W0-4.4：发布门禁校验 + 生成版本快照）
export function publishPrintTemplate(id, remark) {
  return request({
    url: '/print/template/' + id + '/publish',
    method: 'put',
    params: { remark: remark }
  })
}

// 回滚到历史版本（生成新版本发布，不覆盖历史）
export function rollbackPrintTemplate(templateId, versionId, remark) {
  return request({
    url: '/print/template/' + templateId + '/rollback/' + versionId,
    method: 'put',
    params: { remark: remark }
  })
}

// 模板版本历史
export function listPrintTemplateVersions(id) {
  return request({
    url: '/print/template/' + id + '/versions',
    method: 'get'
  })
}

// 记录一次打印预览（W0-4.4：正式打印前必须有预览记录；templateId 为模板主键）
export function recordPrintPreview(templateId, deliveryOrderId) {
  return request({
    url: '/print/template/' + templateId + '/preview',
    method: 'post',
    params: { deliveryOrderId: deliveryOrderId }
  })
}

// ==================== W0-6 模板导入导出与资源治理 ====================

// 导出模板为开放 JSON 包（脱敏，可选携带历史版本）
export function exportPrintTemplate(id, includeVersions) {
  return request({
    url: '/print/template/' + id + '/export',
    method: 'get',
    params: { includeVersions: includeVersions || false }
  })
}

// 导入模板包（全有或全无安全校验、20MB 上限、禁止网络资源、导入后未绑定草稿）
export function importPrintTemplate(packageJson) {
  return request({
    url: '/print/template/import',
    method: 'post',
    data: packageJson,
    headers: { 'Content-Type': 'application/json' }
  })
}

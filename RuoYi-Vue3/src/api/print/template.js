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

// ==================== 客户日报表打印优化（PT-1） ====================

// 按打印主体键解析打印模板（后端三级绑定：客户+点 > 客户 > 全局默认，同形态已发布）
// bizKey: matrix:{customerId}:{date} / point:{customerId}:{deptId}:{date}
// 返回 { printForm, templateId, templateName, reportViewId, matchGlobalDefault, candidates, warning }
export function resolvePrintTemplate(bizKey) {
  return request({
    url: '/print/resolve-template',
    method: 'get',
    params: { bizKey: bizKey }
  })
}

// 打印数据预览（真实订单数据，模板预览用；ticket 为 scope=preview 的预览票据）
// bizKey: matrix:{customerId}:{date} / point:{customerId}:{deptId}:{date}
// 返回 { head, columns, rows }——与真实打印取数同源，仅供页面展示
// P4（打印模块重构）：
export function fetchPrintPreviewData(bizKey, ticket, rowsType) {
  return request({
    url: '/print/previewData',
    method: 'get',
    params: { bizKey: bizKey, ticket: ticket, rowsType: rowsType || 'long' }
  })
}

// ==================== P2 数据契约与物化 ====================

// 查询打印数据契约（字段字典 / 数据集 / 参数）：设计器字段面板与模板生成器共用
// form: MATRIX | FLAT；rowsType: LONG | WIDE（仅 MATRIX 有意义）
export function getPrintContract(form, rowsType) {
  return request({
    url: '/print/template/contract',
    method: 'get',
    params: { form: form || 'FLAT', rowsType: rowsType || 'LONG' }
  })
}

// 按数据契约重新物化模板接线（幂等）：对齐数据集 URL/转换器/参数 + 补齐打印回执钩子
export function materializePrintTemplate(id) {
  return request({
    url: '/print/template/' + id + '/materialize',
    method: 'put'
  })
}

// 生成打印模板骨架（P3 动态生成）：按形态+客户实际结构产出设计 JSON，替代导入静态样板
// data: { printForm, rowsType, customerId, deliveryDate, title, paper, layout }
// 返回 { printForm, rowsType, slots, columns, designJson }
export function generatePrintTemplate(data) {
  return request({
    url: '/print/template/generate',
    method: 'post',
    data: data
  })
}

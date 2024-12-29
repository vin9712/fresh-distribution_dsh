import request from "@/utils/request";

// 分页查询打印模板列表
export function pageTemplate(query) {
  return request({
    url: "/print/template/page",
    method: "get",
    params: query,
  });
}

// 查询打印模板列表
export function listTemplate(query) {
  return request({
    url: "/print/template/list",
    method: "get",
    params: query,
  });
}

// 查询打印模板详细
export function getTemplate(id) {
  return request({
    url: "/print/template/" + id,
    method: "get",
  });
}

// 获取或生成打印模板单号
export function genTemplateCode(query) {
  return request({
    url: '/print/template/code',
    method: 'get',
    params: query
  })
}

// 新增打印模板
export function addTemplate(data) {
  return request({
    url: "/print/template",
    method: "post",
    data: data,
  });
}

// 修改打印模板
export function updateTemplate(data) {
  return request({
    url: "/print/template",
    method: "put",
    data: data,
  });
}

// 删除打印模板
export function delTemplate(id) {
  return request({
    url: "/print/template/" + id,
    method: "delete",
  });
}

import request from "@/utils/request";

// 分页查询打印任务列表
export function pageTask(query) {
  return request({
    url: "/print/task/page",
    method: "get",
    params: query,
  });
}

// 查询打印任务列表
export function listTask(query) {
  return request({
    url: "/print/task/list",
    method: "get",
    params: query,
  });
}

// 查询打印任务详细
export function getTask(id) {
  return request({
    url: "/print/task/" + id,
    method: "get",
  });
}

// 新增打印任务
export function addTask(data) {
  return request({
    url: "/print/task",
    method: "post",
    data: data,
  });
}

// 修改打印任务
export function updateTask(data) {
  return request({
    url: "/print/task",
    method: "put",
    data: data,
  });
}

// 删除打印任务
export function delTask(id) {
  return request({
    url: "/print/task/" + id,
    method: "delete",
  });
}

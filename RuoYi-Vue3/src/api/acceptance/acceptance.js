import request from '@/utils/request'

// 分页查询验收单列表
export function pageAcceptance(query) {
  return request({
    url: '/acceptance/page',
    method: 'get',
    params: query
  })
}

// 查询验收单列表
export function listAcceptance(query) {
  return request({
    url: '/acceptance/list',
    method: 'get',
    params: query
  })
}

// 查询验收单详细
export function getAcceptance(id) {
  return request({
    url: '/acceptance/' + id,
    method: 'get'
  })
}

// 查询验收单明细列表
export function listAcceptanceItems(id) {
  return request({
    url: '/acceptance/' + id + '/items',
    method: 'get'
  })
}

// 按送货单生成验收单（一单一验）
export function createAcceptance(data) {
  return request({
    url: '/acceptance',
    method: 'post',
    data: data
  })
}

// 录入/修改验收单（仅草稿，后端重算金额与损耗）
export function updateAcceptance(data) {
  return request({
    url: '/acceptance',
    method: 'put',
    data: data
  })
}

// 提交验收单（同组订单 → ACCEPTED）
export function submitAcceptance(id) {
  return request({
    url: '/acceptance/' + id + '/submit',
    method: 'put'
  })
}

// 删除验收单（仅草稿）
export function delAcceptance(id) {
  return request({
    url: '/acceptance/' + id,
    method: 'delete'
  })
}

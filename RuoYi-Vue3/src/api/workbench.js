import request from '@/utils/request'

// 查询工作台待办汇总
export function getWorkbenchSummary() {
  return request({
    url: '/workbench/summary',
    method: 'get'
  })
}

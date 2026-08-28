import request from '@/utils/request'

// 查询工作台待办汇总
export function getWorkbenchSummary() {
  return request({
    url: '/workbench/summary',
    method: 'get'
  })
}

// 送货单生成异常告警（S14/Q36：最近一次 DELIVERY_GENERATE 失败/部分失败时返回记录，正常返回 null）
export function getJobAlert() {
  return request({
    url: '/workbench/job-alert',
    method: 'get'
  })
}

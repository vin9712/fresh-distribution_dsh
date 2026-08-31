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

// 待验收提醒列表（W0-3.2）：已送达未验收送货单，逐行带 reminderLevel 0无/1黄/2红
export function getPendingAcceptance() {
  return request({
    url: '/workbench/pending-acceptance',
    method: 'get'
  })
}

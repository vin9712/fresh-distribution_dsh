import request from '@/utils/request'

// 原订单关联的下月调整单摘要（蓝图 §2「月结调整追溯」：客户+结算月粒度，订单归月=最近已提交验收单 accept_date 所在月）
export function getOrderAdjustmentSummary(saleOrderId) {
  return request({
    url: '/month-adjustment/by-order/' + saleOrderId,
    method: 'get'
  })
}

import request from '@/utils/request'

// 读取录单草稿（后端为主、localStorage 兜底断网场景）
export function getSaleOrderDraft(draftKey) {
  return request({
    url: '/order/saleDraft/' + encodeURIComponent(draftKey),
    method: 'get'
  })
}

// 保存（幂等覆盖）录单草稿
export function saveSaleOrderDraft(data) {
  return request({
    url: '/order/saleDraft',
    method: 'post',
    data: data
  })
}

// 删除录单草稿
export function removeSaleOrderDraft(draftKey) {
  return request({
    url: '/order/saleDraft/' + encodeURIComponent(draftKey),
    method: 'delete'
  })
}

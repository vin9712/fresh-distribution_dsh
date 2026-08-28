import request from '@/utils/request'

/**
 * W0-4.1：签发短时一次性打印票据（ptk_ 前缀，TTL 300 秒）
 * 替代原先在 URL 上携带长期 JWT 打开 JimuReport 的方式；
 * 打开报表视图后由 JimuReport 桥接一次性兑换，数据接口凭票据 + 单据绑定取数。
 *
 * @param {Object} data { deliveryOrderId?: Number, templateId?: Number }
 * @returns {Promise<{ticket: String}>}
 */
export function issuePrintTicket(data) {
  return request({
    url: '/print/ticket',
    method: 'post',
    data: data || {}
  })
}

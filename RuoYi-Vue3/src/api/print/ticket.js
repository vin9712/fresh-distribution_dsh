import request from '@/utils/request'

/**
 * W0-4.1：签发短时一次性打印票据（ptk_ 前缀，TTL 300 秒）
 * 替代原先在 URL 上携带长期 JWT 打开 JimuReport 的方式；
 * 打开报表视图后由 JimuReport 桥接一次性兑换，数据接口凭票据 + 取数主体绑定取数。
 *
 * 取数主体二选一：
 * - deliveryOrderId：D-055 前的历史送货单打印；
 * - bizKey：D-055 视图化打印主体键（总单 matrix:{customerId}:{date}、点单 point:{customerId}:{deptId}:{date}）。
 *
 * @param {Object} data { deliveryOrderId?: Number, bizKey?: String, templateId?: Number }
 * @returns {Promise<{ticket: String}>}
 */
export function issuePrintTicket(data) {
  return request({
    url: '/print/ticket',
    method: 'post',
    data: data || {},
    // 票据是短时一次性凭据，允许同一主体连续签发（预览弹窗会为「数据面板 + 版式 iframe」各签一张），
    // 关闭 RuoYi 防重复提交拦截，否则第二张会被「数据正在处理，请勿重复提交」拦下。
    headers: { repeatSubmit: false }
  })
}

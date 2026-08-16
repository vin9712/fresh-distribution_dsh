import request from '@/utils/request'

// 销售日报：按配送日期、按客户+配送点分组
export function dailySale(deliveryDate) {
  return request({
    url: '/report/dailySale',
    method: 'get',
    params: { deliveryDate }
  })
}

// 客户对账单：客户+起止日期
export function customerStatement(params) {
  return request({
    url: '/report/customerStatement',
    method: 'get',
    params: params
  })
}

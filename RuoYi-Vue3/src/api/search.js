import request from '@/utils/request'

// 全局搜索（Ctrl+K）：客户 / 商品 / 订单 分组命中
export function globalSearch(keyword, limit, config) {
  return request({
    url: '/search/global',
    method: 'get',
    params: { keyword, limit },
    signal: config?.signal,
    ...config
  })
}

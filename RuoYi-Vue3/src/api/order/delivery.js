import request from '@/utils/request'

// 分页查询送货单列表
export function pageDelivery(query) {
  return request({
    url: '/order/delivery/page',
    method: 'get',
    params: query
  })
}

// 查询送货单列表
export function listDelivery(query) {
  return request({
    url: '/order/delivery/list',
    method: 'get',
    params: query
  })
}

// 查询送货单详细
export function getDelivery(id) {
  return request({
    url: '/order/delivery/' + id,
    method: 'get'
  })
}

// 查询送货单明细列表（按商品合并行）
export function listDeliveryDetail(deliveryId) {
  return request({
    url: '/order/delivery/' + deliveryId + '/detail',
    method: 'get'
  })
}

// 按配送日期生成送货单（仅汇总已确认订单）
export function generateDelivery(deliveryDate) {
  return request({
    url: '/order/delivery/generate/' + deliveryDate,
    method: 'post'
  })
}

// 按勾选订单生成送货单（销售订单列表页抽屉，配送日期可调整）
export function generateDeliveryByOrders(data) {
  return request({
    url: '/order/delivery/generate-by-orders',
    method: 'post',
    data: data
  })
}

// 标记打印（print_count + 1）
export function printDelivery(id) {
  return request({
    url: '/order/delivery/' + id + '/print',
    method: 'put'
  })
}

// 打印信息：三级绑定解析模板 + 联数
export function printInfoDelivery(id) {
  return request({
    url: '/order/delivery/' + id + '/printInfo',
    method: 'get'
  })
}

// 标记送达（旧入口：未打印单送达会被拒绝，请改用 deliveredDelivery）
export function deliverDelivery(id) {
  return request({
    url: '/order/delivery/' + id + '/deliver',
    method: 'put'
  })
}

// 标记送达（S14：未打印 PENDING 送达时必传免纸原因 {noPrint: {reasonCode, remark}}）
export function deliveredDelivery(id, data) {
  return request({
    url: '/order/delivery/' + id + '/delivered',
    method: 'put',
    data: data
  })
}

// 作废送货单（S14/T4：PENDING/PRINTED 可作废，原因必填，来源分配释放）
export function voidDelivery(id, data) {
  return request({
    url: '/order/delivery/' + id + '/void',
    method: 'post',
    data: data
  })
}

// 按客户+日期生成/补单（S14/T3 统一生成：三态自动分支，返回 GenerateResultVO）
export function generateDeliveryForCustomer(customerId, deliveryDate) {
  return request({
    url: '/order/delivery/generate/customer/' + customerId + '/' + deliveryDate,
    method: 'post'
  })
}

// 来源视图：聚合行 + 展开的来源订单/行/分配量（历史单 sources 为空）
export function listDeliverySources(id) {
  return request({
    url: '/order/delivery/' + id + '/sources',
    method: 'get'
  })
}

// 解析送货单打印拆分配置（W0-2.2：无配置时按批次策略推导默认值+自动生成结构）
export function getDeliveryPrintConfig(id) {
  return request({
    url: '/order/delivery/' + id + '/print-config',
    method: 'get'
  })
}

// 保存送货单打印拆分配置（W0-2.2：仅未打印PENDING单可改，每次保存追加版本记录）
export function saveDeliveryPrintConfig(id, data) {
  return request({
    url: '/order/delivery/' + id + '/print-config',
    method: 'put',
    data: data
  })
}

// 查询打印拆分配置版本记录（W0-2.2：最新在前）
export function listDeliveryPrintConfigVersions(id) {
  return request({
    url: '/order/delivery/' + id + '/print-config/versions',
    method: 'get'
  })
}

// 恢复自动生成结构（W0-2.2：仅未打印PENDING单可恢复，追加版本记录）
export function restoreDeliveryPrintStructure(id) {
  return request({
    url: '/order/delivery/' + id + '/print-config/restore',
    method: 'post'
  })
}

// 客户日总表（S14/D-027/28：标准品名+总量+各点小计，无价格，内部配货/采购视图）
export function batchView(customerId, date) {
  return request({
    url: '/order/delivery/batch/view',
    method: 'get',
    params: { customerId: customerId, date: date }
  })
}

// 新增送货单
export function addDelivery(data) {
  return request({
    url: '/order/delivery',
    method: 'post',
    data: data
  })
}

// 修改送货单
export function updateDelivery(data) {
  return request({
    url: '/order/delivery',
    method: 'put',
    data: data
  })
}

// 删除送货单
export function delDelivery(id) {
  return request({
    url: '/order/delivery/' + id,
    method: 'delete'
  })
}

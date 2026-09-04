import request from '@/utils/request'

// ==================== 送货单据（历史单证，D-055 后只读） ====================

// 分页查询送货单列表
export function pageDelivery(query) {
  return request({
    url: '/order/delivery/page',
    method: 'get',
    params: query
  })
}

// 批次分组聚合分页（D-043：主行=客户+配送日期=批次，聚合张数/状态数/合计/打印形态/提醒最高级）
export function batchPageDelivery(query) {
  return request({
    url: '/order/delivery/batch-page',
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

// 来源视图：聚合行 + 展开的来源订单/行/分配量（历史单 sources 为空）
export function listDeliverySources(id) {
  return request({
    url: '/order/delivery/' + id + '/sources',
    method: 'get'
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

// 候选打印模板（P1/D-048：后端出候选，替代前端复刻绑定过滤；含命中全局默认告警标记）
export function printCandidatesDelivery(id) {
  return request({
    url: '/order/delivery/' + id + '/print-candidates',
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

// 作废送货单（S14/T4，仅历史单证：PENDING/PRINTED 可作废，原因必填，来源分配释放）
export function voidDelivery(id, data) {
  return request({
    url: '/order/delivery/' + id + '/void',
    method: 'post',
    data: data
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

// ==================== 客户日总表三口径（D-055 视图化：数据源=订单明细） ====================

// 矩阵总表（D-044/D-047/D-051 + D-055：行=菜品（订单明细五元组合并行）、列=配送点（含空列）、格=应送量）
// 纸面不打单价与金额，同名多行以备注列「档①」区分；页面与打印共用同一数据
export function deliveryMatrix(customerId, deliveryDate) {
  return request({
    url: '/order/delivery/batch/' + customerId + '/' + deliveryDate + '/matrix',
    method: 'get'
  })
}

// 配货总表（S14/D-027/28：标准品名+总量+各点小计，无价格，内部配货/采购视图）
export function batchView(customerId, date) {
  return request({
    url: '/order/delivery/batch/view',
    method: 'get',
    params: { customerId: customerId, date: date }
  })
}

// 点单视图（D-055：客户+日期+配送点 的订单明细行，含加单/换货/退货标记）
export function pointViewDelivery(customerId, deptId, date) {
  return request({
    url: '/order/delivery/batch/point-view',
    method: 'get',
    params: { customerId: customerId, deptId: deptId, date: date }
  })
}

// ==================== 打印分界（D-055） ====================

// 打印分界登记：每次打开打印视图后记一条（总单 customerDeptId 传空，点单传配送点）
export function markDeliveryPrinted(data) {
  return request({
    url: '/order/delivery/print-log',
    method: 'post',
    data: data
  })
}

// 打印分界查询：该 客户+日期(+配送点) 是否已打印
export function getDeliveryPrintState(customerId, deliveryDate, customerDeptId) {
  return request({
    url: '/order/delivery/print-state',
    method: 'get',
    params: {
      customerId: customerId,
      deliveryDate: deliveryDate,
      customerDeptId: customerDeptId
    }
  })
}

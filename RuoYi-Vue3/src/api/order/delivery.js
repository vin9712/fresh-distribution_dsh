import request from '@/utils/request'

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

// 按配送日期生成送货单（仅汇总已确认订单）
export function generateDelivery(deliveryDate) {
  return request({
    url: '/order/delivery/generate/' + deliveryDate,
    method: 'post'
  })
}

// 生成前预览「待生成清单」（客户维度：一行=一个客户；customerId 传空=当日全部客户，只读不落库）
export function groupPreview(query) {
  return request({
    url: '/order/delivery/group-preview',
    method: 'get',
    params: query
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

// 候选打印模板（P1/D-048：后端出候选，替代前端复刻绑定过滤；含命中全局默认告警标记）
export function printCandidatesDelivery(id) {
  return request({
    url: '/order/delivery/' + id + '/print-candidates',
    method: 'get'
  })
}

// ==================== 打印包（P2/D-050） ====================

// 建打印包（客户+配送日期；已有未完成包则复用）
export function createPrintPackage(data) {
  return request({
    url: '/order/delivery/print-package',
    method: 'post',
    data: data
  })
}

// 查打印包（含任务清单与合计）
export function getPrintPackage(packageId) {
  return request({
    url: '/order/delivery/print-package/' + packageId,
    method: 'get'
  })
}

// 打印包列表（客户+日期）
export function listPrintPackage(query) {
  return request({
    url: '/order/delivery/print-package/list',
    method: 'get',
    params: query
  })
}

// 汇总预览
export function previewPrintPackage(packageId) {
  return request({
    url: '/order/delivery/print-package/' + packageId + '/preview',
    method: 'post'
  })
}

// 开始打印
export function startPrintPackage(packageId) {
  return request({
    url: '/order/delivery/print-package/' + packageId + '/start',
    method: 'post'
  })
}

// 单张回执
export function receiptPrintTask(taskId, data) {
  return request({
    url: '/order/delivery/print-package/task/' + taskId + '/receipt',
    method: 'post',
    data: data
  })
}

// 逐张改模板/份数
export function updatePrintTask(taskId, data) {
  return request({
    url: '/order/delivery/print-package/task/' + taskId,
    method: 'put',
    data: data
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

// 客户日总表（S14/D-027/28：标准品名+总量+各点小计，无价格，内部配货/采购视图）
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

// 矩阵总表（D-044/D-047/D-051：行=菜品明细行、列=配送点快照（含空列）、格=分配量）
// 纸面不打单价与金额，同名多行以 (档①) 区分；页面与打印共用同一数据
export function deliveryMatrix(customerId, deliveryDate) {
  return request({
    url: '/order/delivery/batch/' + customerId + '/' + deliveryDate + '/matrix',
    method: 'get'
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

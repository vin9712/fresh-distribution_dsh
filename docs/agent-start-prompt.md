# Agent 开工提示词（D-055 送货单视图化后）

> **用途**：下次让 AI/Agent 接手本项目时，先给它看这份；它读完即可理解项目当前状态、约定与易踩坑，避免重复调研或走错方向。
> **最近更新**：2026-09-03（D-055 送货单视图化落地后）

---

## 一、项目一句话

生鲜配送 ERP（RuoYi-Vue3 前端 + Spring Boot 后端，核心模块 `lin-distribution`）。**上次大重构 = D-055 送货单视图化**：

> 送货单已**不是独立单证**，而是**订单的视图**——数据源 = 订单明细（`t_sale_order_detail`）。文员下好单数据即存在，**无需生成/定时/补单**。

## 二、开工必读（按顺序）

1. `docs/01-design/订单-送货-验收链路开发进度.md` —— **最后一条 = 最新状态**（2026-09-03 D-055 完整记录：删除/新增/验证/边界）
2. `docs/01-design/送货单矩阵总表与批次视图设计.md` —— §十八 = D-055 定稿；**§一~§十七 的「A 类单证层」概念已作废**（矩阵/点单打印形态、价档备注列等视图层设计保留）

## 三、当前业务模型（D-055 定稿）

```
订单明细（唯一真相）
  ├─ change_type   0正常(含配送前更新) / 1加单(配送后补充) / 2换货 / 3退货
  ├─ change_group  换货组号（被换行与换入行同组关联）
  ├─ change_remark 变更说明
  ├─ num           应送（订单数）      actual_num 实收（镜像默认0=未填，验收按应送兜底）
  ├─ 客户日总表页 /order/batch = 唯一入口（矩阵 / 配货 / 点单 三口径）
  │    ├─ 矩阵：行=菜品、列=客户下属配送点（父级单位 parent_id=0 除外）、格=数量、无单列排后
  │    ├─ 点单：客户+日期+点 → 订单明细行（订单号+应送+实收+标记tag）+ 打印 / 验收入口
  │    └─ 打印：总单矩阵模板（<客户>总单）/ 点单模板（全部通用，实时取数）
  ├─ 配送后变更 = DeliveryChangeService（加单/换货/退货，仅已确认订单 status=1）
  │    ├─ 加单：新行 change_type=1（应收 num + 实收 actual_num）
  │    ├─ 换货：被换行标 change_type=3 + 换入行标 change_type=2（同 change_group）
  │    ├─ 退货：原行标 change_type=3（应送/实收归 0）
  │    └─ 例外：实收>应收（现场多加）正常，不标记
  ├─ 验收 = 客户+日期+配送点（方案乙，一维一验）
  │    ├─ 应送行 = 订单明细（含标记），实收=文员录入
  │    └─ AcceptanceService#createByCustomerPoint(customerId, deptId, date)
  └─ 打印分界 = t_delivery_print_log（存在记录=已打印=配送后，变更需带标记）
```

## 四、已删除（不要再引用）

| 已删 | 说明 |
|---|---|
| `DeliveryGenerationService(+Impl)` | 生成/补单/作废重建/三态分支 |
| 4 个生成接口 + 2 个定时任务（23:30/06:00） | `generate*`/`group-preview`；`generateTomorrowDeliveryOrder`/`generateTodayDeliveryOrder` |
| `t_delivery_source_item` 台账写入 | 源报告仅保留历史（D-055 不写新） |
| 打印包全套（P2/D-050） | `DeliveryPrintPackage/Task*` + 前端 `printPackageDrawer.vue` |
| 打印拆分配置（W0-2.2） | `DeliveryPrintConfig*`（前端 0 调用的死代码） |
| 打印资源登记（W0-6） | `PrintAsset*`（前端 0 调用） |
| Row `selectMissedConfirmedOrders`/`selectConfirmedByCustomerAndDate`/`selectDraftOrdersByDate` | 生成专用查询 |
| 生成/打印包/资源配置相关前端 | sale/delivery 页生成入口、资源管理弹窗 |

## 五、报表数据库约定

- **测试基线**：`mvn -pl lin-distribution test` = **239/239 全绿**（需 **JDK 17**：`export JAVA_HOME=/opt/homebrew/opt/openjdk`）
- **改 Mapper XML 后必须** `mvn -pl lin-distribution install -DskipTests` **再重启后端**（否则 ~/.m2 陈旧 jar → 新接口 404）
- **真机环境**：`./dev.sh build` → 后端 8090 + 前端 1025（`lsof -i :8090,1025` 查状态）；改码后 `pkill -f lin-entry` + `nohup mvn -pl lin-entry spring-boot:run ...`
- **sql 脚本**：`sql/s15~s21` 留在**未提交区**（约定「sql 不动」），已执行远端库（39.96.76.240, `fresh-distribution-dsh`）；新 DDL 需备份（`sql/db_bak/`）后手动执行，不主动改
- **前端构建**：`cd RuoYi-Vue3 && npm run build:prod`（0 error 基线）

## 六、关键接口速查（D-055 后）

| 功能 | 接口 |
|---|---|
| 矩阵 | `GET /order/delivery/batch/{customerId}/{deliveryDate}/matrix` |
| 配货 | `GET /order/delivery/batch/view?customerId&date` |
| 点单 | `GET /order/delivery/batch/point-view?customerId&deptId&date` |
| 单据列表 | `GET /order/delivery/batch-page`（批次聚合）/ `/list` |
| 验收创建 | `POST /acceptance/create-by-point`（customerId/customerDeptId/deliveryDate） |
| 配送后加单 | `POST /order/delivery-change/{orderId}/supplement` |
| 配送后换货 | `POST /order/delivery-change/{orderId}/exchange` |
| 配送后退货 | `POST /order/delivery-change/{orderId}/return` |
| 打印数据 | `GET /print/deliveryData`（点单）/ `/print/deliveryMatrixData`（矩阵，head 含 c1Name~c6Name） |
| 工作台 | `GET /workbench/pending-acceptance`（订单维度） |

## 七、待办/边界（下次可能接的）

1. **点单针式双列三联模板**：待硬件（W0-4.2/4.3 本地打印助手）到位后在 JimuReport 配针式版式（每列 10 行、三联）
2. **历史送货单数据**：保留只读（审计/对账），新流程不写 `t_delivery_order`；若彻底清理需单独迁移方案
3. **本地 dev 分支 14 个 commit 未 push**
4. 测试产生的临时数据（YS20260903002 等）可清理/保留作演示
5. `order/delivery` 页面组件保留（路由兼容），菜单已下线；若确认不需要可删组件

## 八、开工提示词（复制用）

```
参考 docs/agent-start-prompt.md（它概述项目状态与约定）。
本项目是生鲜配送 ERP（RuoYi-Vue3 + Spring Boot，模块 lin-distribution），
最近大重构为 D-055 送货单视图化：送货单=订单的视图（数据源 t_sale_order_detail），
客户日总表页 /order/batch 是唯一入口，配送后变更以标记（add/exchange/return）附加订单明细，
验收=客户+日期+点。开工前：
1. 读 docs/01-design/订单-送货-验收链路开发进度.md 最后一条（最新状态）
2. 检查并保持基线：mvn -pl lin-distribution test 239 全绿（JDK17: JAVA_HOME=/opt/homebrew/opt/openjdk）
3. 改 Mapper XML 必须 mvn install 后重启后端；sql（s15~s21）不主动执行
4. 需要真机验证：/dev.sh build → 后端 8090 + 前端 1025
任务：<在这里写具体任务>
```

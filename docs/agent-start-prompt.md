# Agent 开工提示词（D-055 收尾：三口径同源 + 打印接通后）

> **用途**：下次让 AI/Agent 接手本项目时，先给它看这份；它读完即可理解项目当前状态、约定与易踩坑，避免重复调研或走错方向。
> **最近更新**：2026-09-04（D-055 收尾落地：矩阵/配货改订单明细口径、打印按客户+日期(+点)取数、打印分界登记、s15~s23 脚本入库）

---

## 一、项目一句话

生鲜配送 ERP（RuoYi-Vue3 前端 + Spring Boot 后端，核心模块 `lin-distribution`）。**上次大重构 = D-055 送货单视图化**：

> 送货单已**不是独立单证**，而是**订单的视图**——数据源 = 订单明细（`t_sale_order_detail`）。文员下好单数据即存在，**无需生成/定时/补单**。

**2026-09-04 收尾**：视图化当时只改了「点单」口径与验收，矩阵/配货两口径与打印仍绑在旧送货单表上（新订单看不到、打印打不开）——现已全部改为**订单明细同源**，打印主体从「单据」改为「客户+日期(+点)」。

## 二、开工必读（按顺序）

1. `docs/01-design/订单-送货-验收链路开发进度.md` —— **最后一条 = 最新状态**（2026-09-04 D-055 收尾完整记录：改动/验证/未做清单）
2. `docs/01-design/送货单矩阵总表与批次视图设计.md` —— **§十九 = 收尾定稿**、§十八 = D-055 定稿；**§一~§十七 的「A 类单证层」概念已作废**（矩阵/点单打印形态、价档备注列等视图层设计保留）

## 三、当前业务模型（D-055 + 收尾定稿）

```
订单明细（唯一真相）
  ├─ change_type   0正常(含配送前更新) / 1加单(配送后补充) / 2换货 / 3退货
  ├─ change_group  换货组号（被换行与换货行同组关联）
  ├─ change_remark 变更说明
  ├─ num           应送（订单数）      actual_num 实收（镜像默认0=未填，验收按应送兜底）
  ├─ 客户日总表页 /order/batch = 唯一入口（矩阵 / 配货 / 点单 三口径，全部读订单明细）
  │    ├─ 矩阵：行=菜品（订单明细按五元组合并，不同价必拆行）、列=客户下属配送点（父级单位 parent_id=0 除外）、
  │    │        格=SUM(num)、无单列排后；读口径 status>=1（非草稿即应送），纯退货行（全组=0）不进矩阵
  │    ├─ 配货：同数据源、不拆价不显价（D-027/28），打印走浏览器打印本页
  │    ├─ 点单：客户+日期+点 → 订单明细行（订单号+应送+实收+标记tag+已打印标识）+ 打印 / 验收入口
  │    └─ 打印：总单矩阵模板（标题 <客户>总单）/ 点单模板（FLAT，验收数留空列）——均按 客户+日期(+点) 实时取数
  ├─ 历史日期（D-055 前生成过送货单）→ 矩阵/配货自动回退旧口径（t_delivery_order_detail + source_item）只读展示
  ├─ 配送后变更 = DeliveryChangeService（加单/换货/退货，仅已确认订单 status=1）
  │    ├─ 加单：新行 change_type=1（应收 num + 实收 actual_num）
  │    ├─ 换货：被换行标 change_type=3 + 换入行标 change_type=2（同 change_group）
  │    ├─ 退货：原行标 change_type=3（应送/实收归 0）
  │    └─ 例外：实收>应收（现场多加）正常，不标记
  ├─ 验收 = 客户+日期+配送点（方案乙，一维一验）
  │    ├─ 应送行 = 订单明细（含标记），实收=文员录入
  │    └─ AcceptanceService#createByCustomerPoint(customerId, deptId, date)   ← 写口径仍 status=1
  └─ 打印分界 = t_delivery_print_log（前端每次打印后 POST /order/delivery/print-log 登记；有记录=已打印=配送后）
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
| **（09-04）`DeliveryBatchService#refreshLayout`** | 布局定格随生成服务退役；矩阵按启用点实时推导，`layout_json` 只读历史快照 |
| **（09-04）Row `selectByCustomerAndDateForUpdate` / `selectActiveByCustomerAndDate`** | 生成专用（UPSERT 锁行 / 三态判定） |
| **（09-04）前端 4 个死组件** | `order/delivery/generatePreviewDrawer.vue`、`generatePreviewList.vue`、`order/sale/deliveryGenerateDrawer.vue`、`partner/customer/customerDeliveryDrawer.vue` |
| **（09-04）`api/order/delivery.js` 11 个死函数** | 4 个 generate + 7 个 print-package（指向已删接口，调用必 404） |

## 五、报表数据库约定

- **测试基线**：`mvn -pl lin-distribution test` = **246/246 全绿**
  - **JDK 17 必需**：macOS `export JAVA_HOME=/opt/homebrew/opt/openjdk`；Windows 本机 mvn 已默认 `D:\Java\jdk17\azul-17.0.13`
  - **计数要 `clean` 后才准**：`target/surefire-reports` 里 2026-08-23 的 `DeliverySkuOverrideServiceImplTest` 陈旧报告会被捞起（多算 2 例），`target/classes/mapper/DeliverySkuOverrideMapper.xml` 同理
- **改 Mapper XML 后必须** `mvn -pl lin-distribution install -DskipTests` **再重启后端**（否则 ~/.m2 陈旧 jar → 新接口 404 / 旧 XML 生效）；**改了兄弟模块（lin-admin 的 SecurityConfig 等）要全量 `mvn install -DskipTests`**（曾踩：`/print/deliveryMatrixData` 放行配置在陈旧 lin-admin jar 里 → 401）
- **真机环境**：`./dev.sh build`（Win 用 `./dev_windows.sh build`）→ 后端 8090 + 前端 1025（`lsof -i :8090,1025`；Win `netstat -ano | grep :8090`）；改码后 `pkill -f lin-entry` + `nohup mvn -pl lin-entry spring-boot:run ...`（**dev.sh start 前台阻塞会被 shell 超时杀进程组，务必 nohup 后台**）
- **sql 脚本**：`sql/s15~s23` **已于 2026-09-04 补齐入库**（此前只存在于远端库）；`init_all.sql` 追加 [35]~[41] 节，已在本地 MySQL 5.7 空库全链路重建验证（0 错误 + 14 项 schema 断言 + 双跑幂等）。约定不变：**不主动执行远端**，新 DDL 需备份（`sql/db_bak/`）后人工执行
- **远端库**：39.96.76.240:3306 / `fresh-distribution-dsh`（MySQL 8.0，`ONLY_FULL_GROUP_BY` 开启 → 新写 GROUP BY 查询必须合规）；本机 `application-local.yml` 已指向远端库
- **前端构建**：`cd RuoYi-Vue3 && npm run build:prod`（0 error 基线）

## 六、关键接口速查（D-055 收尾后）

| 功能 | 接口 |
|---|---|
| 矩阵 | `GET /order/delivery/batch/{customerId}/{deliveryDate}/matrix` |
| 配货 | `GET /order/delivery/batch/view?customerId&date` |
| 点单 | `GET /order/delivery/batch/point-view?customerId&deptId&date` |
| 单据列表 | `GET /order/delivery/batch-page`（批次聚合）/ `/list`（历史单证只读） |
| 验收创建 | `POST /acceptance/create-by-point`（customerId/customerDeptId/deliveryDate） |
| 配送后加单 | `POST /order/delivery-change/{orderId}/supplement` |
| 配送后换货 | `POST /order/delivery-change/{orderId}/exchange` |
| 配送后退货 | `POST /order/delivery-change/{orderId}/return` |
| 打印票据 | `POST /print/ticket`，body `{deliveryOrderId}`（历史）**或** `{bizKey}`（D-055：`matrix:{客户}:{日期}` / `point:{客户}:{点}:{日期}`） |
| 打印数据 | `GET /print/deliveryMatrixData?customerId&deliveryDate&colBlock&ticket`（总单）/ `GET /print/deliveryData`+`/deliveryHead?customerId&customerDeptId&deliveryDate&ticket`（点单）——均保留 `deliveryOrderId` 历史主体，参数按 String 容错 |
| 打印分界 | `POST /order/delivery/print-log`（登记）/ `GET /order/delivery/print-state?customerId&deliveryDate&customerDeptId?`（是否已打印） |
| 工作台 | `GET /workbench/pending-acceptance`（订单维度，print_time 取打印日志） |

> JimuReport 数据集 URL 与新参数声明在 `sql/s22_print_data_params.sql`（**远端尚未执行**）；未执行前，报表页取数仍按旧 URL（只传 deliveryOrderId），D-055 新主体打印需在报表设计器里同步数据集 URL 才生效。

## 七、待办/边界（下次可能接的）

1. **执行 s22 / s23 到远端库**（备份后）：否则新主体打印的 JimuReport 数据集取不到参数；s23 B 段（DROP 退役表）默认注释保护
2. **验收提交后的回写缺口**（已查实未改，需业务确认）：点单验收 `deliveryOrderId=null` → `submit` 的 `syncActualMirror` 与订单状态回写双双空转（实收不回写 `t_sale_order_detail.actual_*`、订单停在 status=1 不进 ACCEPTED）；结算以验收单 `total_amount` 为准故不影响金额，但点单页「实收」列不会更新
3. **打印后编辑护栏未联动**：`checkOrderEditable` 不看打印分界，「已打印仍可直接改单」不被拦截（只以带标记入口引导）
4. **点单针式双列三联模板**：待硬件（W0-4.2/4.3 本地打印助手）到位后在 JimuReport 配针式版式（每列 10 行、三联）
5. **客户页「送货单组单策略」两字段**（docScopeType/docMergeSameItem）与提示语在 D-055 后已无实际作用，待确认是否下线
6. **历史送货单数据**：保留只读（审计/对账），新流程不写 `t_delivery_order`；彻底清理需单独迁移方案
7. `order/delivery` 页面组件保留（路由兼容），菜单已 s21 下线；确认不需要可删组件
8. 测试临时数据（YS20260903002、t_print_package/t_print_task 里的 09-02 验证数据）可清理

## 八、开工提示词（复制用）

```
参考 docs/agent-start-prompt.md（它概述项目状态与约定）。
本项目是生鲜配送 ERP（RuoYi-Vue3 + Spring Boot，模块 lin-distribution），
D-055 送货单视图化 + 09-04 收尾已落地：送货单=订单的视图（数据源 t_sale_order_detail），
客户日总表页 /order/batch 是唯一入口（矩阵/配货/点单三口径同源），
打印按 客户+日期(+配送点) 实时取数（票据 bizKey），打印分界记 t_delivery_print_log。开工前：
1. 读 docs/01-design/订单-送货-验收链路开发进度.md 最后一条（最新状态）
2. 检查并保持基线：mvn -pl lin-distribution test 246 全绿（JDK17；计数需 clean 后才准）
3. 改 Mapper XML 必须 mvn install 后重启后端；改兄弟模块要全量 install；sql 不主动执行远端
4. 需要真机验证：dev(_windows).sh build → 后端 8090 + 前端 1025（nohup 后台启动）
任务：<在这里写具体任务>
```

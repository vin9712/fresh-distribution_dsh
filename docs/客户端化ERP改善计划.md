# 客户端化 ERP 改善计划

> 依据：`客户端化ERP优化蓝图.md`（2026-08-26 需求澄清与技术评估稿）
> 本计划将蓝图 §8 分期路线（S0–S3）与 §5 痛点清单展开为可执行、可验收的任务计划。
> 状态：计划稿（待评审排期）。**W0 阶段后端基座已完成（2026-08）：W0-1 价格口径简化、W0-4.1 打印票据化、W0-2.1 撤回级联、W0-2.2 打印拆分配置、W0-2.3 合单合并排序、W0-2.5 已确认采购直接调整、W0-2.6 验收异常字典与作废回退（部分短收放宽不强制，替代 D-013/G8 双向必填）、W0-2.7 下月调整单、W0-3.1 按客户月结、W0-3.2 待验收提醒、W0-3.3 经营概览、W0-4.4 模板管理与发布门禁（后端基座）。W0-2.4 由 S14 覆盖。**待办（前端，另推进）**：W0-4.4 模板设计器交互/纸张分页精确校验/预览确认回执闭环、W0-5 前端基建（RuoYi-Vue3）；⛔ W0-4.2/4.3 硬件实机验证阻塞；⏸️ W0-6 后置至 S2**

---

## 1. 目标与总体策略

- **北极星指标**：熟练文员完成 50 行常规订单 ≤ 5 分钟；真实 HP A4 与 Epson 针式多联样张按模板正确输出。
- **策略**：
  1. 不重写为 Electron，维持浏览器部署 + 可选本地打印助手边界；
  2. 先做安全与设计同步基线（S0），再做订单工作台（S1），再打通日结与打印（S2），最后管理与报表（S3）；
  3. 每阶段以真实文员走查 + 验收清单收口，不与业务使用并行抢改动窗口。

## 2. 现状速览（代码事实，2026-08 核对）

| 事项 | 现状 | 与蓝图差距 |
|---|---|---|
| 订单编辑页 | `ruoyi-ui/src/views/order/sale/detail.vue` 约 1200 行，vxe-table 单文件承载全部逻辑 | 无 `SplitWorkspace` 通用分屏组件，布局固定、不可记忆 |
| 快捷键 | keydown/keyup 监听散落在 20+ 个文件（订单、送货、模板、系统页各自监听） | 无集中注册、无作用域优先级，存在冲突风险 |
| 草稿 | 销售订单已有 localStorage 自动保存/恢复 | 缺用户命名空间、版本号、容量治理、跨 PC 冲突保护 |
| 打印 | JimuReport 已集成（`lin-distribution` 后端含 PrintTemplate/Delivery 系列服务与测试） | 鉴权方式、Epson/HP 实机验证、本地助手未落地 |
| 前端组件 | `components/PrintModule` 已存在 | 预览→确认→任务回执链路未闭环 |
| 状态管理 | Pinia 已装但未统一使用边界 | 需在实施新能力前统一，避免多套全局状态并存 |

## 3. 阶段计划

### S0：设计与安全基线（预计 2–3 周）

> 蓝图 §8-S0 共 39 条，按下述 6 个工作包归组推进。

**W0-1 价格口径简化（阻塞项，最先做）**
| # | 任务 | 产出 | 验收 |
|---|---|---|---|
| 0.1.1 | 下线配送点覆盖价与报价模板：菜单、接口、SQL、页面 | 变更清单 + 回滚脚本 | 全系统仅存“客户正式报价”一种取价口径 |
| 0.1.2 | 权威设计文档（DESIGN.md）、操作手册同步该决策 | 文档 diff 送审 | 蓝图“待同步项”关闭 |
| 0.1.3 | 报价草稿→发布状态机、有效期重叠校验、作废→复制重发布 | 接口 + 前端页面 | 冲突商品发布前逐行提示 |
| 0.1.4 | 订单快照字段服务端不可变校验 + 回归用例 | 后端校验 + 测试 | 报价变更不改已确认订单行 |

**W0-2 单据流转规则补齐**
| # | 任务 | 产出 |
|---|---|---|
| 0.2.1 | 撤回已确认订单 → 级联扣除/作废未打印采购单与送货单（含空单自动作废“订单撤回”） | 服务端事务 + 审计 |
| 0.2.2 | 送货单打印拆分配置：默认按配送点；跨点合单仅 A4；针式单点每页 10 条；版本记录 + 恢复自动结构 | 配置模型 + 版本表 |
| 0.2.3 | 合单行合并规则（SKU+规格+单位+单价相同才并）、合单排序（行数最多订单为基准） | 生成服务 + 单测 |
| 0.2.4 | 打印后改单禁改、作废重开新单号关联原单、作废原因审计 | 状态机 + 列表筛选 |
| 0.2.5 | 已确认采购单直接修改：操作日志 + 前后金额记录 + 报表重算 | 日志 + 重算测试 |
| 0.2.6 | 验收异常原因字典（拒收/短收缺货/临时加货/计量差异）；作废验收回退订单至已送达 | 字典 + 状态机 |
| 0.2.7 | 下月调整单（独立单号、草稿/提交、应收与采购成本分项留痕）+ 原订单关联摘要 | 新实体 + 页面 |

**W0-3 月结与工作台提醒**
| # | 任务 | 产出 |
|---|---|---|
| 0.3.1 | 按客户月结：预览验收/调整单 + Excel 导出（统一 + 客户模板）+ 月结后冻结 | 月结页面 |
| 0.3.2 | 待验收提醒：打印后 2h 黄色、当天 11:30 红色（所有自然日）；工作台卡片 + 列表行标色 | 定时检查 + 前端 |
| 0.3.3 | 经营概览：区分已/未月结金额；待确认成本不计毛利 | 报表口径调整 |

**W0-4 打印安全与验证（首期最高风险项）**
| # | 任务 | 产出 | 验收 |
|---|---|---|---|
| 0.4.1 | 打印鉴权改短时一次性票据，废除 URL 携带 JWT | 票据服务 + 报表放行改造 | 历史日志中不再出现长期 token |
| 0.4.2 | 现场 Epson LQ-630K/630KII + 预印多联连续纸验证；HP A4 验证（依《本地打印助手技术方案》H0–H4） | 验证记录 + 纸张实测值 | 按 §9 打印可靠性指标 |
| 0.4.3 | 依 0.4.2 结果决定：成熟打印服务 or 自研轻量助手（Windows 托盘、配对码、签名任务、回执） | 选型结论 + 原型 | 回执成功/失败分离统计 |
| 0.4.4 | 模板管理：草稿/测试/发布状态、版本、三级绑定（客户+配送点→客户→全局）、强制预览、测试水印、发布门禁（必填字段/纸张/分页/长文本溢出校验） | 模板服务 + 设计器 | 每次正式打印前有预览记录 |

**W0-5 前端基建（S1 前置）**
| # | 任务 | 产出 |
|---|---|---|
| 0.5.1 | 抽取 `SplitWorkspace` 通用分屏组件（拖拽、最小宽度、布局持久化、恢复默认） | 新组件 + 演示页 |
| 0.5.2 | 快捷键服务：集中注册、全局>工作区>表格 优先级、中文输入组合态兼容、冲突表 | 快捷键模块 + 冲突表文档 |
| 0.5.3 | 草稿工具治理：用户命名空间、版本号、容量上限/过期清理、恢复校验（蓝图 §5-P0） | 草稿 util 重构 |
| 0.5.4 | 全局搜索请求竞态修复：序号/AbortController + 防抖（蓝图 §5-P1） | 搜索组件修复 |
| 0.5.5 | 统一状态管理边界约定（Pinia 与现有方式划界），写入前端规范文档 | 规范文档 |

**W0-6 模板导入导出与资源治理（可后置至 S2 期间）**
按蓝图 §8-S0 第 28–39 条批量落：字段白名单与弃用迁移、导入安全校验（全有或全无、20MB 上限、脱敏）、Logo/底图文件目录管理、毫米坐标体系、设备映射分层配置。

### S1：订单桌面工作台（预计 2 周，依赖 W0-5）

| # | 任务 | 验收 |
|---|---|---|
| 1.1 | `detail.vue` 拆分改造：左侧订单头+明细表，右侧常用商品/最近订单/搜索面板，接入 SplitWorkspace | 左右可拖拽、宽度本机记忆 |
| 1.2 | 键盘焦点流：商品选择→数量→单价→下一行；复制行/插入行/删除行/批量粘贴/重复上一行/撤销最近编辑 | 全程不碰鼠标完成一单 |
| 1.3 | 手工定价留痕：报价来源、原建议价、必填原因、操作者/时间，确认时提示并写日志 | 审计可追溯 |
| 1.4 | 头字段变更提示：客户/配送点/日期变化影响商品池与取价时明确提示 | 走查确认 |
| 1.5 | 草稿可见性：自动保存、恢复、清理、提交后删除均可解释 | 断网/刷新演练通过 |
| 1.6 | 真实文员走查：50 行订单 ≤ 5 分钟计时验证（蓝图 §9） | 达标记录归档 |

### S2：日结闭环与打印落地（预计 2–3 周，依赖 S0-W0.4 与 S1）

| # | 任务 |
|---|---|
| 2.1 | 待办链梳理优化：已确认订单 → 批量采购 → 送货打印 → 送达登记 → 独立验收 |
| 2.2 | 采购批量成本录入优化；到货→待确认成本→已确认成本状态提示 |
| 2.3 | A4 浏览器打印与针式本地打印助手双链路接通（按 0.4.3 选型） |
| 2.4 | 打印失败重试（关联原失败任务）、批量失败立即停止队列并保留清单、重复打印确认、全量审计 |
| 2.5 | 单张强制预览 + 批量汇总预览；预览页可切模板/打印机/份数（本次生效，显式“设为默认”）；助手不可用禁止打印 |
| 2.6 | 设备映射：PC 本地设备映射与偏移校准（按打印机型号+纸张规格）、校准测试页、±20mm 偏移、导出导入须测试打印后启用 |

### S3：管理与报告体验（预计 1–2 周）

| # | 任务 |
|---|---|
| 3.1 | 工作台角色化待办中心（文员/老板差异化） |
| 3.2 | 报表拆分汇总/明细/导出三个请求边界；大范围查询分页/流式导出（蓝图 §5-P2） |
| 3.3 | 周期经营指标：销售额、采购额、损耗额、周期估算毛利（口径标注） |
| 3.4 | 单据类列表页统一：列配置、快捷键、批量操作、空态/加载/错误/越权反馈；QuickTable 与 el-table 范式收敛 |

## 4. 里程碑排期建议

| 里程碑 | 内容 | 出口条件 |
|---|---|---|
| M1（第 3 周末） | W0-1~W0-5 完成 | 价格口径唯一、打印票据安全、SplitWorkspace/快捷键/草稿基建可用 |
| M2（第 5 周末） | S1 完成 + 0.4.2 实机验证结论 | 50 行 ≤ 5 分钟达标；打印技术路线确定 |
| M3（第 8 周末） | S0 余项 + S2 完成 | 真实样张（HP A4 + Epson 多联）验收通过；日结链路闭环 |
| M4（第 10 周末） | S3 完成 | 蓝图 §9 全部指标走查通过 |

> 排期为单人全职投入估算；0.4.2 实机验证依赖打印机到场，需尽早预约，若阻塞可与 S1 并行。

## 5. 依赖与风险

| 风险 | 影响 | 对策 |
|---|---|---|
| Epson 驱动/纸张实测数据未取得（蓝图 §10-1/2） | 针式套打验证无法开始 | 列入现场验证清单，第一时间采集；本地助手选型留两条预案 |
| 打印票据改造涉及 JimuReport 集成方式 | 可能牵动报表放行逻辑 | 先做 0.4.1 技术预演，隔离改造点 |
| `detail.vue` 1200 行单文件拆分回归风险 | 录单是核心业务 | 拆分前补关键路径 E2E/走查清单，灰度切换 |
| 两套表格范式并存 | 交互不一致、学习成本 | 每阶段只迁新页面，不做大爆炸式重写 |
| 需求文档 39 条 S0 细则体量大 | 排期膨胀 | W0-6 允许后置至 S2 期间，其余按工作包串行收口 |

## 6. 验收与追踪

- 每个工作包对应蓝图 §9 验收指标建立走查记录；
- 快捷键冲突表（0.5.2 产出）作为后续页面接入的准入检查；
- 本计划随实施进度更新状态标记（☐/≠/✔），重大设计变更回写 `客户端化ERP优化蓝图.md` 并同步权威设计。

---

## 7. 执行记录

### W0-1 价格口径简化 ✔（2026-08）

| 任务 | 结果 |
|---|---|
| 0.1.1 下线配送点覆盖价与报价模板 | ✔ 取价服务 `PriceQueryServiceImpl` 移除报价模板回退链，仅存客户正式报价单一口径；删除 PriceTemplate 后端全套（domain×3/mapper×3+XML×3/service×2/controller×1）；新增 `sql/w01_price_simplify.sql`（幂等：drop 4 表 + 下线报价模板菜单 + 清理 role_menu），并同步追加至 `init_all.sql` [32]；顺带修复「零价报价」缺口（单价为 0 视为无报价） |
| 0.1.2 同步权威设计与手册 | ✔ `DESIGN.md`（§2.1/§3-7/§6 领域模型/§8 取价规则）、`操作手册.md`（§2.9 标注下线、取价规则重写、术语表）、`文员快速上手指南`、`DEVELOPMENT.md`（表/接口契约）、`agent-development-brief.md`、`录单页交互细化设计.md`、`ui-interaction-spec.md`（§3.9/§3.10 标注下线）、`deepseek_redesign.md`（头部废弃注）；历史进度日志按惯例保留原状 |
| 0.1.3 报价状态机与校验 | ✔ `updateQuoteStatus` 状态机护栏：仅草稿可发布、已发布不可撤回为草稿（作废后复制重发）、已作废不可重复作废；新增发布时同客户同商品有效期重叠校验（`selectOverlappingPublishedQuotes` + 逐 SKU 交叉比对，冲突时列出报价单号与商品名）；前端报价列表移除「撤销」入口改为「作废」并修复 INVALID 状态码 3→2 bug；「作废→复制」复用前端已有 copy 模式（带出原有效期、重置单号） |
| 0.1.4 订单快照不可变校验 | ✔ `updateSaleOrderWithDetails` 新增 `checkSnapshotImmutable`：客户/配送点/单号为快照标识字段不可变更（换客户新开订单）；新增回归用例 4 项 + 取价测试 4 项 + 报价状态机测试 6 项 |

测试：`lin-distribution` 146/146 通过（原 136 + 新增 10）。

### W0-4.1 打印鉴权改短时一次性票据 ✔（2026-08）

| 任务 | 结果 |
|---|---|
| 链路调查 | 现状：① 前端送货单打印/模板设计器/预览均以 `?token=<长期JWT>` 打开 JimuReport（`RuoYi-Vue3/src/views/order/delivery/index.vue`、`print/template/index.vue`），JWT 进入浏览器历史/代理/日志；② `/jmreport/**` permitAll 由 `JimuReportTokenServiceImpl` 校验 URL token；③ `/print/deliveryHead`、`/print/deliveryData` 完全无凭据（凭 deliveryOrderId 可越权拉取含价格数据）；④ `PrintModule` 为 hiprint 试验件未接打印链路，不涉及 token，未改动 |
| 票据服务 | ✔ 新增 `PrintTicketService`/`Impl`（lin-distribution）：`ptk_` 前缀随机票据，Redis `print_ticket:unused:*` TTL 300 秒；首次校验一次性消费后落 `print_ticket:used:*` 600 秒会话宽限（覆盖 JimuReport 单次报表会话多次请求：渲染/数据集回调/导出打印），过期即死不可重放；签发记录当前登录用户为操作者 |
| 签发接口 | ✔ `POST /print/ticket`（PrintController），JWT 过滤器 + `@ss.hasAnyPermi('order:delivery:print,print:template:list')`，入参 `{deliveryOrderId?, templateId?}` 返回 `{ticket}` |
| 报表放行改造 | ✔ `JimuReportTokenServiceImpl` 对 `ptk_` 前缀走票据兑换，否则保留 JWT 兼容链路；`deliveryHead/deliveryData` 改为强校验票据且票据必须与请求 deliveryOrderId 绑定一致（防串单越权，无绑定设计器票据不允许拉业务数据），SecurityConfig 放行保留（JimuReport 服务端回调无 JWT，接口自验票）；`sql/s6_2_print_seed.sql` 与新增 `sql/w04_print_ticket.sql`（幂等，含回滚参考）将数据集 api_url 追加 `&ticket=${ticket}` 透传 |
| 前端 | ✔ 新增 `RuoYi-Vue3/src/api/print/ticket.js`；送货单打印、设计器入口、模板预览三处改为「先签发票据 → URL 只带 ticket」，URL/日志不再出现长期 JWT |
| 测试 | ✔ 新增 `PrintTicketServiceImplTest` 7 项（签发绑定/一次性兑换/宽限复用/过期失效与 JWT 互斥/数据接口绑定校验/宽限语义/票据唯一性）；`mvn -pl lin-distribution test` 153/153 通过（原 146 + 新增 7）；lin-admin/lin-entry 编译通过 |

> 遗留提示：0.4.2 实机验证时需一并确认 JimuReport 数据集 URL 对 `${ticket}` 的占位符替换生效（与既有 `${deliveryOrderId}` 同机制，风险低）；若后续接入本地打印助手，助手侧同样只接受本票据体系派发的短时签名任务，不得持有长期 JWT。

### W0-2.1 撤回已确认订单级联扣除/作废未打印采购单与送货单 ✔（2026-08）

| 任务 | 结果 |
|---|---|
| 链路调查 | 现状：① 撤回（CONFIRMED→DRAFT）由 `updateSaleOrderStatus` 用 `existsValidAllocation` 一票否决——只要订单进入任何非作废送货单即拒撤，无级联能力；② 送货侧有 `t_delivery_source_item` 逐单分配台账（S14），具备扣除重算基础；③ 采购侧仅 `source_order_ids` JSON 头部引用 + 按「sku+品名+规格+单位」聚合明细（`selectSummaryByOrderIds`），无逐单分配台账；④ 采购单无打印态与作废态（DRAFT/CONFIRMED/STOCKED），空单作废需扩展状态机 |
| 撤回级联服务 | ✔ 新增 `OrderWithdrawCascadeService`/`Impl`：拆分 `validateOrderWithdrawable`（无副作用预检）与 `cascadeOnOrderWithdraw`（级联执行）两阶段。拒绝线：订单被已打印/已送达送货单占用，或被已入库（STOCKED）采购单引用（采购无打印态，已入库视同已执行）；`updateSaleOrderStatus` 先对全部待撤回订单预检、再逐单级联，任一失败整体回滚 |
| 送货扣除 | ✔ 仅待打印（PENDING）单参与级联：软删该订单 source_item 分配（唯一键含 is_deleted，释放后重新生成不撞键）→ 有剩余分配的聚合行按「num=Σ剩余分配量、amount=price×num」重算 → 无剩余分配的行删除 → 整单无有效明细则自动作废（共享单保留、不影响其他订单，蓝图「共享单据撤回」） |
| 采购扣除 | ✔ 未入库（草稿/已确认）自动采购单按采购汇总键（与 `selectSummaryByOrderIds` 聚合口径一致）扣减数量，小计=新数量×原单价，扣至 0/负（含手工改小后超扣）钳零删行；`source_order_ids` 移除被撤订单并重算 total_amount；手工采购单（source_type=2）天然不参与 |
| 空单自动作废 | ✔ 扣除后无任何明细的送货单/采购单自动作废，原因固定「订单撤回」（蓝图「空关联单据」）；送货单复用既有 void_reason/void_by/void_time 审计字段；采购单新增同构三列，状态机增加 `VOIDED(3,"已作废")`，作废单不可确认/入库/删除，仅可查看 |
| 审计 | ✔ 作废人/时间/原因落单据字段 + `PUT /order/sale/status` 补 `@Log`（businessType=UPDATE）进 sys_oper_log + 服务 log.info 输出级联摘要（作废/扣除单号清单）；`WithdrawCascadeResultVO` 返回执行结果 |
| SQL | ✔ 新增 `sql/w02_withdraw_cascade.sql`（幂等：purchase_order 作废三列按 INFORMATION_SCHEMA 判重补列 + 字典 delivery_void_reason 增加 order_withdraw「订单撤回」）并同步追加至 `init_all.sql`（purchase_order 建表语句含列 + [33] 字典段落） |
| 测试 | ✔ 新增 `OrderWithdrawCascadeServiceImplTest` 13 项（已打印/已送达/已入库拒撤、独占空单作废、共享扣除重算、采购汇总键扣除、零行删除、超扣钳零、作废单跳过、手工单不参与等）；`SaleOrderServiceImplTest` G4 撤回用例重写为级联口径（4 项）；`mvn -pl lin-distribution test` 167/167 通过（原 153 + 新增 14）；lin-admin/lin-entry 编译通过 |

> 设计取舍备注：① 采购单无打印态，「未打印」口径落为「未入库（DRAFT/CONFIRMED 可扣）」；已入库单引用的订单拒撤，需走退货单/调整流程；② 编辑护栏（`checkOrderEditable`）维持「CONFIRMED 已进有效送货单拒改」，与蓝图「已确认改单：先撤回至草稿再改」衔接，未放开直接编辑；③ 被撤订单的采购扣除以「订单明细快照聚合键」为依据，若采购单事后被手工增删明细导致键不匹配，按「不足则尽、不误伤其他行」处理（钳零删行）。

### W0-2.2 送货单打印拆分配置（配置模型 + 版本表）✔（2026-08）

| 任务 | 结果 |
|---|---|
| 设计边界 | 蓝图「送货单打印拆分配置/送货调整版本/自动结构恢复」；范围收敛为「配置模型 + 版本表」后端基座（规则校验与排序/合并在 W0-2.3 生成服务落地）。拆分配置按「一张送货单一份当前配置」建模，与批次组单策略快照（scope_type/merge_same_item）解耦——同一配送日期批次内可对具体送货单单独调整打印顺序、分页与介质；每次保存/恢复写一份版本记录，承载「配置变更必须审计」与「一键恢复自动生成结构」 |
| 模型与规则 | ✔ 新增常量 `DeliveryPrintSplitMode`（DEFAULT_PER_DEPT/CROSS_POINT_MERGE/MAX_ROWS_SPLIT）、`DeliveryPrintMediaType`（A4/DOT_MATRIX）、`DeliveryPrintRule`（默认按配送点、默认 A4、针式固定每页 10 条、分页上限 50）；新增领域 `DeliveryPrintConfig`（t_delivery_print_config：delivery_order_id 唯一、split_mode/media_type/rows_per_page/structure_json/auto_generated/版本/逻辑删除）与 `DeliveryPrintConfigVersion`（t_delivery_print_config_version：version_no 自增、快照列、变更说明、操作人/时间） |
| 服务 | ✔ 新增 `DeliveryPrintConfigService`/`Impl`：① `resolveConfig` 只读解析——无已存配置时按批次 scope_type 推导拆分方式（CUSTOMER_DATE→跨点合单、其余→默认按配送点）+ 自动生成结构（明细ID升序）+ 默认 A4/每页10 条，不落库；② `saveConfig` 校验并保存（仅未打印 PENDING 单；跨点合单仅 A4 校验；针式固定每页 10 条校验；detailOrder 必须为该单有效明细全排列）→ UPSERT 当前配置（按 delivery_order_id 锁行 + 唯一键 unq_delivery_order 兜底并发）→ 追加版本记录；③ `listVersions`；④ `restoreAutoStructure` 重算自动结构（明细ID升序）、置 auto_generated=true、追加版本记录（变更说明固定「恢复自动生成结构」）。打印结构 `DeliveryPrintStructure` 序列化明细顺序 + 「第 N/M 张」分页桶（即使仅一页也显示 第 1/1 张） |
| Controller | ✔ 新增 `DeliveryPrintConfigController`（/order/delivery/{id}/print-config）：GET 解析、PUT 保存（@Log UPDATE）、GET /versions 版本列表、POST /restore 恢复自动结构；权限统一 `order:delivery:print` |
| 前端 | ✔ `RuoYi-Vue3/src/api/order/delivery.js` 增加 get/save/listVersions/restore 四个打印配置接口封装（待 S2/S1 打印配置页接入） |
| SQL | ✔ 新增 `sql/w03_delivery_print_split.sql`（幂等 CREATE TABLE IF NOT EXISTS 建两表，含回滚参考）并同步追加至 `init_all.sql`（[34] 段落建表） |
| 测试 | ✔ 新增 `DeliveryPrintConfigServiceImplTest` 14 项（无配置推导默认/跨点总单推导/已存配置返回/送货单不存在、已打印禁改、跨点合单禁针式、最大行数拆分允许针式、针式固定每页10、顺序全排列校验、保存写版本、缺省沿用当前顺序、已送达禁恢复、恢复自动结构重算+追加版本、版本列表）；`mvn -pl lin-distribution test` 181/181 通过（原 167 + 新增 14）；lin-admin/lin-entry 编译通过 |

> 设计取舍备注：① 拆分配置只描述「一张送货单如何拆成打印结构/介质/分页」，订单→送货的合单/合并（五元组合并、按基准订单排序、跨点分区小计）属 W0-2.3 生成服务，本项不重复实现；② 介质校验口径「跨点合单仅 A4、针式仅单点（按点/最大行数拆分均算单点）」；A4 按页高自动分页，structure_json 的每页行数仅记录期望值，真实分页由 JimuReport/打印助手渲染；③ resolveConfig 只读推导不落库，首次保存/恢复才建立配置行与版本 1，避免只读请求写库。

### W0-2.3 合单行合并规则 + 合单排序 ✔（2026-08）

| 任务 | 结果 |
|---|---|
| 合单排序（基准订单） | ✔ `DeliveryGenerationServiceImpl.mergeRows` 重构：按订单分组 → 以同一配送点/总单下商品行数最多订单为基准保持其行顺序，其余订单按下单顺序（orderId 升序）补充未出现商品；五元组合并（sku+品名+单位+规格+单价，不同价必拆行 D-024）沿用，合并多来源行订单号置空（G1 口径）。`orderedGroupIds` 辅助方法处理 基准优先 + 其余升序 |
| 测试 | ✔ `DeliveryGenerationServiceImplTest` 17→19（同点多订单以最多行订单为基准并顺序补充 / 基准共享商品合并数量且保持基准行位置+台账3条）；全工程 `mvn -pl lin-distribution test` **183/183** |

> 设计取舍备注：① 合单规则（SKU+规格+单位+单价相同才并）与 S14 五元组合并一致（品名快照为第 5 元，权威设计 D-024）；跨点分区小计属 W0-4.4 模板渲染侧，不改变生成服务物理结构（A类总单 dept 仅留 source_item）。

### W0-2.5 已确认采购单直接修改：操作日志 + 前后金额 + 报表重算 ✔（2026-08）

| 任务 | 结果 |
|---|---|
| 调整能力 | ✔ 新增 `PurchaseOrderServiceImpl.adjustConfirmedPurchase`：仅已确认（CONFIRMED）单可调；仅允许修改已有明细行数量/单价（禁止增删行、锁定 sku/品名/规格/单位快照），逐行更新并重算小计与总额 |
| 操作日志+前后金额 | ✔ 新增 `t_purchase_modify_log`（before/after_amount + before/after_items 明细快照 JSON + operator/operate_time/remark）；`PurchaseModifyLog` 域/Mapper/XML；每次调整落一条审计（@Log UPDATE + 服务层 log 输出差异） |
| 报表重算 | ✔ 调整即改实时采购数据，经营概览（W0-3.3）按实时表重算受影响周期 |
| 接口 | ✔ `PUT /purchase/{id}/adjust`（purchase:edit + @Log UPDATE）+ `GET /purchase/{id}/modify-logs`（purchase:list）；`PurchaseOrderService` 接口增两方法 |
| SQL | ✔ `sql/w05_purchase_adjust_log.sql`（幂等建表）+ init_all.sql 同步 |
| 测试 | ✔ `PurchaseOrderServiceImplTest` 7→12（调整成功前后金额/明细审计、非已确认拒调、含非已有行拒调、数量为负拒调、日志列表）；全工程 **188/188** |

### W0-2.6 验收异常原因字典 + 作废验收回退 ✔（2026-08）

| 任务 | 结果 |
|---|---|
| 异常原因字典 | ✔ 复用 S14 已建：shortfall（缺货/拒收/损耗/质量问题/错送/其他）与 overage（临时加送/计量差异/录单遗漏/其他），覆盖蓝图「拒收/短收缺货/临时加货/计量差异」 |
| 作废验收回退 | ✔ 复用 S14-T5 `revoke`（SUBMITTED→DRAFT + 审计快照 + 来源订单 ACCEPTED→DELIVERED + 清镜像），实现「作废已提交验收单后关联订单回退至已送达」 |
| **权威设计变更：部分短收不强制** | ✔ 经确认：`resolveReasonType` 改为「实收=0（全部拒收）或超收必填原因；部分短收（0<实收<送货）建议但不强制；无差异清空」，放松蓝图对 D-013/G8「双向必填」的覆盖；负差异仍记短收类型(1)、超收记(2)；新消息「全部拒收必须填写原因」 |
| 测试 | ✔ `AcceptanceServiceImplTest` 29→30（原「损耗为负必须填写原因」改「全部拒收实收为零必须填写原因」+ 新增「部分短收未填原因允许保存且记短收类型」）；全工程 **189/189** |
| 待办 | 差异口径变更需回写权威设计（DESIGN.md D-013/G8 标注为「已修订为 W0-2.6 部分短收不强制」）——已同步至本文档执行记录，DESIGN.md 修订标注留作文档收口项 |

### W0-2.7 下月调整单 ✔（2026-08）

| 任务 | 结果 |
|---|---|
| 模型 | ✔ 新增 `MonthAdjustment`（t_month_adjustment：独立单号 TJyyyyMMddNNN、customer_id、bill_month、receivable_amount、purchase_cost_amount 分项、status 0草稿/1已提交、is_deleted、审计字段） |
| 服务 | ✔ `MonthAdjustmentService`/`Impl`：create（独立单号、客户/结算月校验、金额非负、应收与成本分项留痕）、update（仅草稿）、submit（草稿→已提交，提交后参与对账/经营概览重算）、delete（仅草稿逻辑删除）、list/getById |
| 接口 | ✔ `MonthAdjustmentController`（/month-adjustment）list/get/add/edit/submit/remove，权限 monthAdjustment:list/add/edit/remove |
| SQL | ✔ `sql/w06_month_adjustment.sql`（幂等建表 + 菜单/按钮权限）+ init_all.sql 同步 |
| 测试 | ✔ 新增 `MonthAdjustmentServiceImplTest` 10 项（新增默认草稿+单号、客户不存在、缺结算月、应收成本同时0拒、提交、重复提交拒、已提交禁改/禁删、草稿可删、列表）；全工程 **199/199** |
| 待办 | 「原订单关联摘要 + 提交后立即重算经营概览/客户对账」挂接 W0-3（月结/经营概览）时落地 |

### W0-3.1 按客户月结 ✔（2026-08）

| 任务 | 结果 |
|---|---|
| 归月口径 | ✔ 验收单按 `accept_date` 归月、采购单按 `order_date`（无 customer_id，按月粒度冻结）、下月调整单按 `bill_month` |
| 结算表 | ✔ `t_month_settlement`（客户+结算月唯一、status 0未结/1已结、结算人/时间）；`MonthSettlement` 域/Mapper/XML |
| 服务 | ✔ `MonthSettlementService`/`Impl`：`preview`（该客户该月验收单+调整单+汇总：已提交验收实收 / 调整应收成本净额 / 结算应结金额）、`settle`（upsert 已结，重复结算拒绝）、`isSettled`、`selectList`、`getByCustomerAndMonth` |
| 冻结校验 | ✔ 接入 `AcceptanceServiceImpl.updateDraft/revoke`（验收日期归月）、`MonthAdjustmentServiceImpl.update/delete`（bill_month）、`ReturnOrderServiceImpl.create/updateDraft/submit/deleteByIds`（经原验收单客户+验收日期归月）；`PurchaseOrderServiceImpl.adjustConfirmedPurchase`（无 customer_id，按归属月 existsByMonth 粒度冻结，采购池化口径） |
| 接口 | ✔ `MonthSettlementController`（/month-settlement）list/preview/settle/check/get/export（验收单 Excel 导出）；验收单查询支持 begin/endAcceptDate 范围（月结预览） |
| SQL | ✔ `sql/w07_month_settlement.sql`（幂等建表+菜单权限）+ init_all.sql 同步 |
| 测试 | ✔ 新增 `MonthSettlementServiceImplTest` 6 项；既有 `Acceptance/Purchase/MonthAdjustment/ReturnOrder` 测试补 @Mock MonthSettlementMapper；全工程 **205/205** |

### W0-3.2 待验收提醒（打印2h黄/11:30红） ✔（2026-08）

| 任务 | 结果 |
|---|---|
| 打印时间 | ✔ `t_delivery_order` 加 `print_time` 列（init_all.sql + `sql/w08_pending_acceptance_print_time.sql` 幂等 ALTER）；`DeliveryOrder` 域/XML；`markPrinted` 落打印时间 |
| 提醒计算 | ✔ `PendingAcceptanceReminder`（纯函数）：配送日当天11:30前打印满2h→黄(1)；当天11:30后→红(2)；配送日已过期→红(2)；未来→无(0) |
| 接口 | ✔ `GET /workbench/pending-acceptance`：查已送达未提交验收的送货单列表并逐行标提醒级别/原因；`WorkbenchMapper.selectPendingAcceptance` |
| 测试 | ✔ 新增 `PendingAcceptanceReminderTest` 5 项；全工程 **208/208** |
| 前端 | 工作台卡片与送货单列表行按级别标色随前端页接入（RuoYi-Vue3） |

### W0-3.3 经营概览（已/未月结区分 + 待确认成本不计毛利） ✔（2026-08）

| 任务 | 结果 |
|---|---|
| 口径 | ✔ 验收实收按已提交验收单 `total_amount` 归期；按「客户该月是否已月结」分桶（left join t_month_settlement）；采购总额=非作废采购单 `order_date` 归期；待确认成本=草稿+已确认（未入库）采购单；周期估算毛利=`验收实收−同周期采购`（蓝图 §7.2） |
| 服务 | ✔ `ReportService.overview(beginDate,endDate)` + `ReportVO.OperatingOverview`/`OverviewSettleAmount`；`ReportMapper` 新增 selectOverviewAccepted/Purchase/PendingCost |
| 接口 | ✔ `GET /report/overview?beginDate&endDate`（report:query） |
| 测试 | ✔ 新增 `ReportServiceImplTest` 3 项（已/未月结+估算毛利、待确认成本不计毛利、日期范围校验）；全工程 **208/208** |

### W0-4.4 打印模板管理与发布门禁（后端基座） ✔（2026-08）

| 任务 | 结果 |
|---|---|
| 状态机 | ✔ `PrintTemplate` 加 `status`(0草稿/1已测试/2已发布) 与 `test_watermark`；`PrintTemplateStatus` 枚举。已发布版本在新版本发布前继续使用（`resolveForDeliveryOrder` 仅命中已发布模板，未发布即回退/报错） |
| 版本历史 | ✔ `t_print_template_version`（模板ID+版本号+名称/内容/绑定/联数快照 + 发布人/时间/说明）+ `PrintTemplateVersion` 域/Mapper/XML；发布/回滚均生成新版本快照（不覆盖历史） |
| 发布门禁 | ✔ `validatePublishGate`：名称/内容必填、内容 JSON 可解析、内容长度上限（长文本溢出兜底）、绑定类型 1-3、组合绑定必填配送点、联数≥1 |
| 操作 | ✔ `testPublish`（置已测试+测试水印）、`publish`（门禁校验+置已发布+写版本快照）、`rollback`（回滚历史版本→生成新版本发布）、`listVersions`、`currentPublishedVersion` |
| 强制预览 | ✔ `t_print_preview_log`（模板+送货单+操作人+时间）+ `recordPreview`；正式打印前应调此记录 |
| 编辑/删除护栏 | ✔ 已发布模板不可直接修改（需另存新版本再发布）、不可删除（可停用）；草稿/已测试可改 |
| 接口 | ✔ `PrintTemplateController` 加 `/test-publish`、`/publish`、`/rollback/{versionId}`、`/versions`、`/preview` |
| SQL | ✔ `sql/w09_template_version_preview.sql`（幂等 ALTER 加列 + 建两表）+ init_all.sql 同步 |
| 测试 | ✔ `PrintTemplateServiceImplTest` 3→12（发布门禁通过+版本快照、缺名称/内容/配送点拒绝、测试发布打水印、已发布禁改/禁删、回滚生成新版本、预览记录）；全工程 **217/217** |
| 待办 | 纸张/分页精确校验、测试水印渲染、预览→确认→任务回执闭环、模板设计器状态（草稿/测试/发布）交互——属前端（RuoYi-Vue3）与 JimuReport 设计器侧，另做 |

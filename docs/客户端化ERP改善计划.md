# 客户端化 ERP 改善计划

> 依据：`客户端化ERP优化蓝图.md`（2026-08-26 需求澄清与技术评估稿）
> 本计划将蓝图 §8 分期路线（S0–S3）与 §5 痛点清单展开为可执行、可验收的任务计划。
> 状态：计划稿（待评审排期）。**W0 阶段后端基座已完成（2026-08）：W0-1 价格口径简化、W0-4.1 打印票据化、W0-2.1 撤回级联、W0-2.2 打印拆分配置、W0-2.3 合单合并排序、W0-2.5 已确认采购直接调整、W0-2.6 验收异常字典与作废回退（部分短收放宽不强制，替代 D-013/G8 双向必填）、W0-2.7 下月调整单、W0-3.1 按客户月结、W0-3.2 待验收提醒、W0-3.3 经营概览、W0-4.4 模板管理与发布门禁（后端基座）。W0-2.4 由 S14 覆盖。W0-5.1 SplitWorkspace 前端组件、W0-5.2 快捷键服务、W0-5.3 草稿工具治理、W0-5.4 全局搜索竞态修复已完成（2026-09，见执行记录）。W0-4.4 前端（模板状态交互/发布门禁/预览回执闭环）与 W0-5.5 状态边界规范已完成（2026-09，见执行记录）；2026-08-31 收尾四散项：W0-2.6 DESIGN.md D-013/G8 标注收口、W0-3.2 前端标色接入（工作台卡片+送货单列表）、月结调整单「原订单关联摘要」（客户+结算月粒度）、模板复制约束对齐蓝图（仅全局模板可作底稿）。S1 订单工作台开发完成（2026-09，1.1~1.5，见执行记录；1.6 真实文员 50 行 ≤5 分钟走查待现场验证）；S2 日结闭环部分完成（2026-09，2.1/2.2 完成；2.3~2.6 硬件阻塞，见执行记录）；W0-6 模板导入导出与资源治理完成（2026-09，见执行记录）；⛔ W0-4.2/4.3 硬件实机验证阻塞**

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
| 1.3 | 手工定价留痕：报价来源、原建议价、操作者/时间自动审计（**2026-09 简化：取消必填原因**） | 审计可追溯且不打断录单 |
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
| 待办 | 「原订单关联摘要 + 提交后立即重算经营概览/客户对账」挂接 W0-3（月结/经营概览）时落地；**「原订单关联摘要」已于 2026-08-31 完成（见下方执行记录，客户+结算月粒度），「提交后立即重算」已由 W0-3.3 经营概览按实时表重算覆盖** |

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
| 前端 | ✔ 已接入（2026-08-31，见下方「W0-3.2 前端标色接入」执行记录） |

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
| 待办 | 纸张/分页精确校验、测试水印渲染、预览→确认→任务回执闭环、模板设计器状态（草稿/测试/发布）交互——**前端部分已完成（2026-09，见下方 W0-4.4 前端执行记录）**；JimuReport 设计器侧水印渲染与纸张实测随 W0-4.2 实机验证落地 |

### W0-5.1 SplitWorkspace 通用分屏组件 ✔（2026-09）

| 任务 | 结果 |
|---|---|
| 组件 | ✔ 新增 `RuoYi-Vue3/src/components/SplitWorkspace/index.vue`：左右双面板 + 中缝拖拽（Pointer Events，鼠标/触摸/键盘兼容）；左右最小宽度按像素钳制（`leftMinWidth`/`rightMinWidth`，窗口过窄时保左舍右）；占比持久化到 localStorage，键格式 `splitws:u{userId}:{storageKey}`（蓝图 §2 共享设备按登录用户隔离），值带版本号 `{v:1,r:占比}`；`maxRatio` 限制中缝始终可见；窗口 resize 后按最小宽度重钳不覆盖用户偏好 |
| 恢复默认 | ✔ 双击中缝、键盘焦点下方向键 ±2% 微调、`resetLayout()` 对外暴露（清除存储 + 回默认占比）；演示页提供「恢复默认布局」按钮验证 |
| 演示页 | ✔ `RuoYi-Vue3/src/views/demo/split-workspace/index.vue`：模拟 S1 订单工作台布局（左：订单头+明细表；右：常用商品/最近订单/商品搜索标签页），实时显示占比；路由注册为隐藏常量路由 `/demo/split-workspace`（需登录，不走菜单 SQL，避免演示入口进生产菜单） |
| 验证 | ✔ `npm run build:prod` 构建通过；新增 `tests/e2e-split-workspace.mjs`（Playwright）6 项运行时验证全通过：默认占比 70%、拖拽变比 56.1%、用户命名空间写入 `splitws:u1:demo-split-workspace`（`{v:1,r:0.5605}`）、刷新后记忆保持、方向键微调、恢复默认回 70% 且存储清零 |
| 修复记录 | ✔ 验证中发现并修复：`loadRatio()` 恢复布局后未 emit `change`，导致消费方（@change 同步外部状态）拿到过期占比；已补 emit 后全项通过 |

> 设计取舍备注：① 持久化存「占比」而非像素，适配不同窗口尺寸；最小宽度用像素定义，与拖拽手感一致；② 折叠/收起（TreePanel 有 collapse）未纳入本组件——S1 右侧面板用标签页切换即可，避免首版膨胀；TreePanel 后续可迁移至本组件实现，不在本次范围；③ 键盘微调符合蓝图「纯键盘优先」文化，中缝 `role=separator` + aria-valuenow 可访问性支持；④ 若后续 0.5.3 草稿工具统一封装「用户命名空间存储」util，SplitWorkspace 的键拼接应随之收敛复用。

### W0-5.2 快捷键集中注册服务 ✔（2026-09）

| 任务 | 结果 |
|---|---|
| 服务 | ✔ 新增 `RuoYi-Vue3/src/utils/shortcut.js`：全局唯一 window keydown 监听（懒安装/空时卸载）；作用域 `SCOPE.GLOBAL > WORKSPACE > TABLE`（蓝图 §3.3 优先级）；分发为责任链（handler 返回 false 下传低优先级，处理即自动 preventDefault+stopPropagation）；硬性保护：`isComposing`/`keyCode 229` 输入法组合态不分发、input/textarea/select/contenteditable 焦点默认不分发（ctrl/meta 组合或显式 `allowInInput` 例外）；开发环境同作用域同键重复注册 console.warn；`getShortcutRegistry()`/`findShortcutConflicts()` 供生成注册表与冲突核对 |
| 组合式封装 | ✔ 新增 `src/composables/useShortcuts.js`：onMounted 注册 / onBeforeUnmount 注销 / onActivated-onDeactivated 启停，解决 keep-alive 缓存页失活后快捷键仍生效问题（蓝图「仅由当前活动工作区响应」） |
| 迁移 | ✔ GlobalSearch（Ctrl+K 唿起 + Esc 关弹窗→global）；QuickTable（F2/F3/Ctrl+S/Delete/Esc→table，移除与 GlobalSearch 重复的 Ctrl+K）；TagsView（Esc 退全屏→global）；预览页与 lock.vue 原生监听不涉及链路，未动 |
| 冲突修复 | ✔ 历史冲突三项消除：① Esc 双触发（GlobalSearch 关弹窗同时 QT 清空选择）→ 责任链下传；② Ctrl+K 双重处理 → 统一由 GlobalSearch global 绑定承担；③ F2/F3 输入框内误触（QT 先匹配后判焦点）→ 服务层输入焦点保护统一前置；④ keep-alive 失活仍响应 → 组合式自动启停 |
| 文档 | ✔ 新增 `docs/快捷键规范与冲突表.md`：规范（作用域/优先级/硬性保护/推荐键位）、现状注册表、冲突表（C1~C5 处置记录）、后续页面接入准入检查 8 项、已知未实施项（Ctrl+W、S1 Enter/Tab 焦点流） |
| 验证 | ✔ 构建通过；新增 `tests/e2e-shortcuts.mjs`（Playwright）9 项全通过：Ctrl+K 唿起/Esc 关闭、演示页 F2 切面板/F3 聚焦、输入框内 F2 不触发、IME 组合态不分发（含非组合态对照触发）、QuickTable 页 F2 新增弹窗/F3 聚焦搜索；回归 `e2e-split-workspace.mjs` 通过；`npm run build:prod` 通过 |

> 设计取舍备注：① 优先级语义为「同键多作用域时高优先级先分发」，而非「低作用域完全屏蔽」——配合责任链返回 false，覆盖「弹窗未打开时 Esc 仍可退出全屏/清空选择」的真实场景；② Ctrl+K 从 QuickTable 移除属行为收敛：@global-search 事件无任何页面监听，删除无破坏；③ demo 页 F2/F3 为 workspace 作用域示范，S1 订单工作台接入时应按冲突表准入检查登记；④ 全局唯一监听用冒泡阶段（window keydown），与原实现一致，不捕获框架内部事件。

### W0-5.3 草稿工具治理 ✔（2026-09）

| 任务 | 结果 |
|---|---|
| 用户命名空间 | ✔ `RuoYi-Vue3/src/utils/saleDraft.js` 重构：key 格式升级为 `saleDraft:u{userId}:order:{id}\|new:{deptId}`，共享 PC 草稿按登录用户完全隔离（蓝图 §2）；导出 `draftKeyOf()` 替代 detail.vue 两处手拼 key 字符串；旧格式 key 在 7 天宽限内一次性迁移到当前用户（保留 24h 内新鲜记录）后不再读取 |
| 版本号 | ✔ 记录携带 `ver`（结构版本，当前 2，不匹配即丢弃）与 `rev`（单草稿修订号，每次保存 +1）；后端双写同步携带 ver/rev，为后续多 PC 冲突「比较并选择版本」（蓝图草稿冲突决策，属 S1 交互）提供依据 |
| 容量治理 | ✔ 单条草稿序列化后 >256KB 不落本地（后端双写不受影响，console.warn）；每用户本地草稿上限 20 条，超限按 savedAt 淘汰最旧；listDrafts/saveDraft 时惰性执行清扫 |
| 过期清理 | ✔ 过期时间 24h→7 天（兼顾蓝图「长假恢复」痛点，后端草稿同口径）；过期/损坏记录在读取与保存时自动清除 |
| 恢复校验 | ✔ 新增 `validateDraft()` 结构校验（ver/rev/savedAt/details 数组/字段类型）；`restoreDraft()` 拒绝非当前用户命名空间的 key（防 URL 携带他人草稿 key 越权读取）；损坏记录返回 null 并清除 |
| 兼容性 | ✔ 本地 API 函数签名不变，detail.vue 仅两处 removeDraft 硬编码 key 换 draftKeyOf，其余调用无感升级；后端 draftKey 自动获得用户命名空间（服务端旧草稿随过期自然淘汰） |
| 演示页 | ✔ 新增 `src/views/demo/draft-workspace/index.vue` + 隐藏路由 `/demo/draft-workspace`：真实读写 saleDraft 工具，可验证命名空间/rev 自增/超大拒绝/上限淘汰/恢复校验/损坏清除 |
| 验证 | ✔ 构建通过；新增 `tests/e2e-sale-draft.mjs`（Playwright）8 项全通过：key 含用户段、rev=2 自增、超大草稿本地拒绝、22 条压到 20 条上限、恢复校验通过且消费删除、损坏 JSON 清除、他人命名空间 key 不入当前用户列表；回归 e2e-smoke/e2e-split-workspace/e2e-shortcuts 全部通过（顺带执行 `sql/w08` 幂等脚本修复本地库缺 print_time 列的环境问题） |

> 设计取舍备注：① 恢复后消费删除草稿为 detail.vue 既有设计（restoreDraftIntoForm 显式 removeDraft），工具层不隐式消费；② 过期 7 天与蓝图「24h 过期」痛点不矛盾——痛点在于数据无治理导致的不可预期失效，后端双写为主后本地 7 天只是更长久的断网兜底；③ 「同一草稿多 PC 修改时逐行比较选择版本」的冲突交互属蓝图草稿冲突决策，依赖 rev 字段，在 S1 录单工作台落地；④ 后端 t_sale_order_draft 表无 user 字段，隔离依赖 draftKey 前缀，若后续需要服务端强隔离可加 user_id 列（不在本次前端基建范围）。

### W0-5.4 全局搜索竞态修复 ✔（2026-09）

| 任务 | 结果 |
|---|---|
| 现状确认 | ✔ `GlobalSearch/index.vue` 存在蓝图 §5-P1 两处风险：① `let seq = 0` 声明未使用，`await globalSearch` 无竞态防护，慢响应可覆盖新输入结果；② 每个按键直接发请求，无防抖；③ `jump()` 用 `window.open(url)` 裸跳，绕过路由 base 与守卫 |
| 防抖 | ✔ 输入 250ms 防抖后才发请求；清空关键词立即取消防抖与在途请求并回提示态 |
| 序号 + AbortController | ✔ 双保险：请求序号（只有最新一次请求的响应允许写入结果/结束 loading）+ AbortController（发新请求前取消上一个在途请求）；被取消/过期响应静默处理，不误展示「未找到」空态；`onClosed`/`onBeforeUnmount` 取消在途与防抖 |
| 请求层配套 | ✔ `request.js` 错误拦截器增加取消静默（`axios.isCancel`/`ERR_CANCELED` 不弹错误 toast）；`api/search.js` `globalSearch` 支持透传 `signal` |
| 路由跳转统一 | ✔ `jump()` 改为 `router.resolve(url)` 后 `window.open(resolved.href)`，URL 不含代理前缀、遵循路由 base；`flushDraft` 联动不变 |
| 验证 | ✔ 构建通过；新增 `tests/e2e-global-search.mjs`（Playwright route 拦截制造乱序：搜「番」延迟 1500ms、搜「土豆」即时）5 项全通过：防抖后 2 字符仅 1 次请求、慢旧响应不覆盖快新响应（最终首条=新鲜土豆）、无取消报错/5xx、跳转 URL 经 resolve 无代理前缀、清空回提示态；回归 e2e-shortcuts/e2e-smoke 全部通过 |

> 设计取舍备注：① 序号与 Abort 叠加而非二选一：序号防「旧响应后到覆盖」，Abort 省「无谓请求占用」并在响应层兜底，两者任一单独失效都不会错序；② 防抖 250ms 为经验值（蓝图未定），若录单场景反馈偏慢可降至 150ms；③ 未改动的 `HeaderSearch` 无搜索请求链路，不涉及。

### W0-4.4 打印模板前端：状态交互 + 发布门禁 + 预览回执闭环 ✔（2026-09）

| 任务 | 结果 |
|---|---|
| 后端配套小改 | ✔ ① `PrintTemplateMapper.xml` `selectBindTemplate` 增加 `and status = 2`——草稿/已测试模板不再遮蔽已发布模板的回退匹配（与解析注释「未发布模板不参与」对齐，行为修复）；② `selectPrintTemplateList` 支持 `status` 过滤（供前端查询已发布模板列表）；③ `printInfo` 补返 `templateRecordId`（模板主键，供预览留痕接口）/`customerId`/`deliveryPointId`（供前端筛选可切换模板）。`mvn -pl lin-distribution test` 217/217 通过，lin-admin/lin-entry 编译通过 |
| 模板状态交互 | ✔ `RuoYi-Vue3/src/views/print/template/index.vue`：列表新增状态列（草稿/已测试/已发布 tag）与测试水印标记列、状态筛选；行操作：测试发布（确认后置已测试+水印，并自动打开带水印预览）、发布（仅已测试可发布，门禁清单对话框 + 版本说明，发布成功提示新版本号）、版本历史（版本表格 + 逐版本「回滚此版」）；已发布模板修改/删除按钮禁用 + tooltip（后端门禁兜底）；编辑对话框显示当前状态并提示「保存后回草稿」；API 封装新增 testPublish/publish/rollback/versions/previewRecord（`api/print/template.js`） |
| 纸张/分页门禁 | ✔ 发布对话框内置 4 项门禁清单（必填字段含页码第 N/M 张、纸张介质 A4 纵向/针式预印多联参考 241×140、分页规则针式每页 10 条跨页重复表头/单页显示第 1/1 张、长商品名边界数据无溢出），全勾选方可提交；后端 `validatePublishGate` 同步校验。精确分页/溢出校验仍以 JimuReport 设计器 + 实机测试为准（W0-4.2） |
| 预览回执闭环 | ✔ `order/delivery/index.vue` `handlePrint` 重构为对话框三步流程（el-steps）：① 选择模板（默认三级绑定解析模板，可切换其他已发布模板仅本次生效）→「打开预览」（签发票据开预览窗 + 调 `/print/template/{id}/preview` 落预览留痕）；② 预览后解锁「确认打印」（新票据开打印视图）；③ 回执确认「本次打印是否成功」——成功才 `printDelivery` 记录打印次数并推进状态，失败不计数提示重试（蓝图「失败不增加成功打印次数」） |
| 验证 | ✔ `npm run build:prod` 构建通过；后端 217/217 通过 |

> 设计取舍备注：① 预览与打印使用两次签发的独立票据（票据一次性，预览会话已消费首次票据）；② 「设为默认」更新三级绑定需后端绑定管理接口（蓝图 S2-2.5 预览页临时调整完整版），本次仅实现「切换模板仅本次生效」，默认绑定变更暂走模板管理页；③ 份数本次生效需打印任务实体（t_print_task 已建表未接入），待本地打印助手（W0-4.3）阶段统一落地；④ 打印回执为用户确认制（浏览器打印无程序化回执），本地助手接入后替换为程序化回执。

### W0-5.5 统一状态管理边界约定 ✔（2026-09）

| 任务 | 结果 |
|---|---|
| 现状盘点 | ✔ ① Pinia 7 个 store（user/permission/tagsView/dict/app/settings/lock）均为 RuoYi 框架层，无业务页自建 store；② 业务页状态为组件 data + API；③ localStorage 使用 11 处：框架（cache.js/settings/lock）、草稿（saleDraft，W0-5.3 已治理）、用户偏好（SplitWorkspace 已带用户命名空间；TreePanel/RightToolbar/QuickTable storageKey 无用户命名空间）；④ provide/inject 仅 layout 内部；⑤ 无 eventBus/mitt；⑥ 键盘事件已由 W0-5.2 集中 |
| 规范文档 | ✔ 新增 `docs/前端状态管理规范.md`：四类状态归属决策表（会话/框架态→Pinia、服务端数据→组件 data+API、用户偏好→localStorage 带用户命名空间、页面瞬时态→组件 data）+ 判定口诀；Pinia 使用细则（框架 store 不扩、业务数据不进 store、新增 store 准入）；localStorage 键格式 `域:u{userId}:{业务键}` + 值带版本号 `v` + 容量口径（64KB 目标/256KB 上限）；provide/inject、window 事件、eventBus（禁止引入）、sessionStorage、路由参数边界；现状盘点与收敛项（TreePanel/RightToolbar/QuickTable 键无用户段，按「触碰即改」推进，不专项排期）；反模式 7 条（review 一票否决）+ 新页面准入检查 7 项 |
| 验证 | ✔ 文档送审；无代码改动，不涉及构建/测试回归 |

### S1 订单桌面工作台 ✔（2026-09，1.1~1.5；1.6 待真实走查）

| 任务 | 结果 |
|---|---|
| 1.1 分屏改造 | ✔ `RuoYi-Vue3/src/views/order/sale/detail.vue` 去除 el-row/el-col 固定栅格，接入 W0-5.1 SplitWorkspace（storage-key=order-sale-detail，default-ratio=0.68，左最小 600px/右最小 320px）：左侧做单区（订单头+明细表）、右侧选单区（常用/最近/搜索三标签页），中缝拖拽占比按登录用户持久化，双击恢复默认 |
| 1.2 键盘焦点流 | ✔ 接入 W0-5.2 集中快捷键服务（WORKSPACE/TABLE 作用域，事件归属护栏 + keep-alive activated/deactivated 启停）：F2 插入空行、F3 聚焦右侧搜索面板、F4 重复上一行、Ctrl+D 复制当前行、Ctrl+Z 撤销最近编辑（快照栈上限 50，进入单元格编辑前自动推入）、Delete 删除当前编辑行、Ctrl+S 保存；Enter 焦点流：数量→单价→下一行商品名→数量（最后一行自动追加空行），vxe keyboard-config isEnter 置 false 避免双跳；批量粘贴：表格容器原生 paste 监听解析多行 TSV（商品名[Tab]数量[Tab]单价[Tab]单位），按客户商品池精确/助记码匹配后批量插入并取价（上限 200 行） |
| 1.3 手工定价留痕 | ✔ 明细行新增 priceSource（quote/manual/temp）、refPrice（原建议价）、priceReason 字段：改价偏离报价或无报价时弹「手工定价原因」必填弹窗（5 个预设原因 + 自定义输入，取消还原价格），确认后单价列显示「手」标签悬浮原因；保存前缺失原因拦截 + 手工行汇总二次确认；后端：`t_sale_order_detail` 加 3 列（`sql/s1_manual_price_audit.sql` 幂等 ALTER，init_all.sql [35] 节同步）、领域/Mapper/insert/update 同步，订单确认（CONFIRMED）时 `logManualPricingOnConfirm` 汇总手工行写操作日志（来源/原建议价/原因/操作者，SecurityUtils 安全回退 system）<br>**⚠ 2026-09 简化（用户反馈「填原因太啭嗦」）：已移除原因必填弹窗、缺失原因拦截与保存前二次确认；改价偏离即直接标记 priceSource=manual + 轻提示（与报价不一致请确认），单价列「手」标签悬浮改为展示「本次价 / 原报价」；priceReason 字段保留（可选、历史兼容），服务端确认日志不再输出原因段，审计仍可追溯谁/何时/原价→现价** |
| 1.4 头字段变更提示 | ✔ 配送日期变更且已录明细时弹确认框（说明影响取价基准、单价快照不自动刷新），取消还原（程序性还原带守卫防二次确认死循环）；客户/配送点变化本就联动重置商品池（既有逻辑） |
| 1.5 草稿可见性 | ✔ 订单头新增草稿状态标签（保存中/已保存悬浮解释：5 秒防抖自动保存、本地+服务器双写、提交后自动清除）；新增「草稿箱」入口（显示草稿数）+ 弹窗（送货单位/订单编号/行数/保存时间/恢复/删除），恢复提示「暂存已消费清除，再次录入后重新自动保存」；保存成功提示明确「草稿已清除」 |
| 验证 | ✔ `npm run build:prod` 构建通过；后端 SaleOrder/DeliveryOrder/PurchaseOrder 相关 53/53 测试通过；新增 `tests/e2e-order-workbench.mjs`（真实订单录入页）：分屏渲染/三标签页/拖拽占比+localStorage 用户命名空间持久化/刷新记忆保持、F3 聚焦搜索面板、草稿箱弹窗、选客户→插商品→改配送日期弹确认、改价→手工定价弹窗→原因为空拦截→选原因确认→「手」标签，全部通过；回归 e2e-shortcuts/e2e-sale-draft/e2e-split-workspace 全部通过<br>**⚠ 2026-09 简化后：e2e 手工定价场景改为断言「不再弹原因弹窗 + 偏离轻提示 + 「手」标签出现」，已重跑通过**<br>**⚠ 2026-09 补强：新增 `tests/e2e-order-save-manual-price.mjs` 真实点「保存」跑通落库链路（选到有商品池的客户→插商品→填数量→改价→保存→接口回查 `price_source=manual`→接口删除清理），并显式检查保存接口 body.code（RuoYi 将后端异常包为 HTTP 200 + code=500，只看状态码会漏判）；该用例当场发现 dev 库未执行 `sql/s1_manual_price_audit.sql` 导致保存报 `Unknown column 'price_source'`** |
| 待办 1.6 | ⏳ 真实文员 50 行订单 ≤5 分钟计时走查（蓝图 §9），需现场验证后归档记录 |

### S2 日结闭环与打印落地 ◐（2026-09，2.1/2.2 完成；2.3~2.6 硬件阻塞）

| 任务 | 结果 |
|---|---|
| 2.1 待办链梳理优化 | ✔ 工作台首页新增「日结待办链」五阶段链路视图：已确认订单（含明日待生成采购子计数）→ 批量采购（待确认+到货待确认成本双计数）→ 送货打印 → 送达登记（已打印未送达）→ 独立验收，节点显示计数徽标与子说明、无待办时降灰，点击直达对应筛选队列（`/order/sale?status=1`、`/purchase`、`/order/delivery?status=0/1`、`/order/acceptance`）；送货单页支持 route.query.status 预置筛选（字符串与字典值匹配）；后端 `WorkbenchSummary`/`WorkbenchMapper.selectSummary` 新增 confirmedOrders/purchaseDraft/purchasePendingCost/pendingMarkDelivered 四个计数 |
| 2.2 采购批量成本录入优化 | ✔ ① 采购列表新增「成本状态」列（已确认=待确认成本/已入库=已确认成本，带悬浮说明，对应蓝图「到货→待确认成本→已确认成本」口径）；② 新增批量入库接口 `PUT /purchase/batch-stock-in`（仅已确认可入库，任一非法整体回滚）+ 前端勾选批量入库（勾选含非已确认行时禁用）；③ 补齐 W0-2.5 前端「调整成本」抽屉（调 `PUT /purchase/{id}/adjust`，仅改数量/单价禁止增删行，实时预览调整前后总额，成功后校验审计日志写入）；④ 供应商补录：草稿/已确认采购单可补录供应商与采购员（`PUT /purchase/{id}/supplier`，后端校验状态与必填其一，@Log 审计，已入库不可改），前端补录对话框 |
| 2.3~2.6 打印双链路/打印任务/预览页调整/设备映射 | ⛔ 均依赖 W0-4.2/4.3 硬件实机验证（HP A4 + Epson 针式）与本地打印助手选型，待硬件到位后实施（2.4 关联 t_print_task 接入，随 2.3 落地） |
| 验证 | ✔ 后端 223/223 测试通过（PurchaseOrderServiceImplTest 新增批量入库×3/供应商补录×3 共 6 个单测）；`npm run build:prod` 构建通过；新增 `tests/e2e-s2-workbench-chain.mjs`：待办链五节点渲染/徽标计数/节点跳转、种子采购单确认后「待确认成本」标签、调整成本抽屉全流程（改数量→保存→审计日志校验）、供应商补录全流程（UI 填写→列表数据校验）、批量入库（勾选→确认→「已确认成本」标签）、送货单 status=1 预置筛选显示「已打印」、独立验收节点跳转，全部通过；回归 e2e-smoke/e2e-order-workbench/e2e-sale-draft/e2e-shortcuts/e2e-split-workspace/e2e-global-search 全部通过 |
| 环境备注 | ✔ dev 库补齐历史迁移漂移：执行 `sql/w02_withdraw_cascade.sql`（purchase_order 作废三列）、从 init_all.sql 抽取 8 张缺失表建表（purchase_modify_log/t_month_adjustment/t_month_settlement/t_print_template_version/t_print_preview_log/delivery_sku_override/t_delivery_print_config/t_delivery_print_config_version）；注意 dev.sh 后端以 ~/.m2 已安装 jar 运行，改后端代码需先 `mvn install` 再 `./dev.sh restart`<br>**✔ 2026-09 新增漂移体检脚本 `tests/check_schema_drift.py`：比对 init_all.sql 期望的表/列与目标库实际结构（含幂等脚本内嵌的 ADD COLUMN），退出码非 0 即存在缺失；配置默认从 `application-local.yml` 的 master 数据源解析，不硬编码口令。已用它确认当前 dev 库无漂移（48 张期望表齐备；t_product_sku 的 5 个旧版残留列属 R1 重构前定义、代码已不引用，列入默认忽略）** |

### W0-6 模板导入导出与资源治理 ✔（2026-09）

| 任务 | 结果 |
|---|---|
| 导出（开放 JSON 包，可选历史版本） | ✔ `GET /print/template/{id}/export?includeVersions=` 返回开放 JSON 包（`format=lin-print-template` + `schemaVersion=1`）；导出前脱敏：content 剔除内部 ID/绑定/操作者等敏感键（`TemplateContentGovernor.sanitize`）；前端行内「导出」按钮，下载 `.json`（含历史版本时 `_full.json`） |
| 导入安全校验（全有或全无） | ✔ `POST /print/template/import`（@RequestBody JSON）：① 20MB 上限（蓝图 39）；② format/schemaVersion 不兼容禁止导入（蓝图 30/38）；③ 每模板校验 name/type(0/1)/renderEngine(jimureport)/copies/content；④ content 经治理器 `validateImport`：schemaVersion 兼容 + 递归扫描禁止网络资源（http(s) 外链且非本地 `/profile/print/assets/` 与 `data:` 前缀）整体拒绝（蓝图 37）；⑤ 全部校验通过后才逐条落库，任一非法整包回滚；导入后为重命名未绑定草稿（名称前缀「导入_原名_时间戳」、bindType=3、customerId=0、status=0 草稿、须重走完整发布门禁，蓝图 38） |
| 字段白名单与弃用迁移 | ✔ `TemplateContentGovernor`：顶层受控字段白名单（schemaVersion/paper/margin/copies/fields/fixedText/logo/background/elements/layout 等，jimureport 结构透传）；弃用字段迁移映射（paperSize→paper、marginMm→margin，迁移后剔除旧键）；导入时 `migrate` 后 `validateImport` |
| 资源治理（Logo/底图） | ✔ 新增 `t_print_asset` 表（init_all.sql [36]）+ `PrintAssetService`/`PrintAssetController`：上传仅 PNG/JPG/JPEG 且 ≤5MB（蓝图 36），存本机文件目录 `profile/print/assets`（绝对路径，修正 transferTo 相对路径问题），返回 URL 供 content 引用；删除前扫描模板/历史版本 content 是否引用该 URL（`PrintTemplateMapper.countContentLike`/`PrintTemplateVersionMapper.countContentLike` LIKE 扫描），被引用则拒绝物理删除（蓝图 37：历史引用资源不可物理删除）；前端「资源管理」弹窗（上传/列表/删除） |
| 验证 | ✔ 后端 246/246 测试通过（新增 `TemplateContentGovernorTest`×11 + `PrintTemplateIoServiceImplTest`×8 + `PrintAssetServiceImplTest`×4 = 23 个单测：迁移/脱敏/网络资源拒绝/数组扫描/资源限制/全有或全无/未绑定草稿/引用保护）；`npm run build:prod` 构建通过；新增 `tests/e2e-w06-template-io.mjs`：上传 PNG 资源→建引用资源模板→被引用资源删除被拒→导出脱敏包→改名导入→未绑定草稿出现（bindType=3/status=0/customerId=0）→资源管理弹窗列表渲染，全部通过；回归 e2e-s2-workbench-chain/e2e-order-workbench/e2e-smoke 全部通过 |
| 坐标体系与设备映射 | ⏸️ 蓝图 34（模板坐标统一毫米）、32-33（设备映射分层/同型号复用）属设计器与硬件范畴：坐标体系已在治理器以 `schemaVersion` + 字段白名单约束（mm 后缀约定），完整毫米坐标校验随设计器与 W0-4.3 本地打印助手校准测试页落地；设备映射依赖 W0-4.2/4.3 硬件，⛔ 阻塞 |
| 环境备注 | ✔ dev 库补齐 t_print_template 的 W0-4.4 列（status/test_watermark，幂等 ALTER via information_schema）；后续又补齐 t_sale_order_detail 的 S1-1.3 三列（执行 `sql/s1_manual_price_audit.sql`，保存订单链路 E2E 发现）；price_reason 列注释已随「取消必填」同步为「可选、不再必填，仅留痕」（脚本与 init_all.sql 一致） |

### W0-2.6 文档收口：DESIGN.md D-013/G8 标注统一 ✔（2026-08-31）

| 任务 | 结果 |
|---|---|
| 标注修订 | ✔ `DESIGN.md` 两处（§7-领域规则第9条、§8-验收清单第8条）统一为标准措辞「**D-013/G8 已修订为 W0-2.6：部分短收不强制（实收=0 或超收必填原因；0<实收<送货建议填写）**」，与 `AcceptanceServiceImpl.resolveReasonType` 现行为一致（实收=0 必填/超收必填/部分短收不强制/无差异清空） |
| 范围说明 | 本项仅动 DESIGN.md；`销售订单与送货单关系设计.md`（D-013 定义表）与 `订单-送货-验收链路详细设计.md`（G8 守卫表/§双吐原因）已有同口径修订标注，不再重复改；改善计划 W0-2.6 执行记录中的「DESIGN.md 修订标注留作文档收口项」待办同步关闭 |

### W0-3.2 待验收提醒前端标色接入 ✔（2026-08-31）

| 任务 | 结果 |
|---|---|
| 口径决策 | ✔ 送货单列表不走工作台接口，改为列表查询时实时计算 reminderLevel：复用同一纯函数 `PendingAcceptanceReminder`（打印满2h黄/当天11:30后红/过期红），保证与 `GET /workbench/pending-acceptance` 完全同口径；仅 status=已送达(2) 且无已提交验收单的行参与分级（批量查 `selectSubmittedDeliveryIds`，已验收行不标），避免 N+1 |
| 后端 | ✔ `DeliveryOrder` 加非落库字段 reminderLevel/reminderReason；`DeliveryOrderServiceImpl.selectDeliveryOrderList` 补 `fillReminderLevel`（非送达行/无候选行直接跳过不查库）；`AcceptanceMapper.selectSubmittedDeliveryIds` 批量查询 |
| 工作台卡片 | ✔ `workbench/index.vue` 待验收卡片下拉分级计数（红 X / 黄 Y 徽标，来自 `/workbench/pending-acceptance` 逐行 reminderLevel 统计，无提醒不占位）；新增 `getPendingAcceptance` API 封装 |
| 送货单列表 | ✔ `order/delivery/index.vue`：① `el-table :row-class-name` 按级别标行色（红 #fef0f0/黄 #fdf6ec）；② 状态列对提醒行**追加**「待验收」色标签（红/黄，不取代原状态标签，竖排不挤），悬浮展示 reminderReason 原因 |
| 运行时自审修复 | ✔ ① 提醒标签由「替代状态标签」改为「追加在状态标签下方」，保留「已送达」状态信息；② 工作台补 `activated()` 刷新（keep-alive 缓存下验收提交返回后分级计数/概览即时更新） |
| 测试 | ✔ 新增 `DeliveryOrderServiceImplTest` 4 项（过期未验收标红/已提交验收单不标/非送达与未来配送日不标/无送达行不查验收单）；`mvn -pl lin-distribution test` **262/262**；`npm run build:prod` 通过；全工程编译通过 |
| E2E | ✔ 新增 `tests/e2e-w032-reminder.mjs`（只读验证，dev 库既有 2 张过期红单）：工作台红徽标计数=API 基线、红级行 row-reminder-red + 提醒标签悬浮原因、标色行数=基线提醒数（已验收送达行不标）、keep-alive 返回计数仍在——**全项通过** |

> 设计取舍备注：① 分级计算放服务层而非 Controller，page/list/export 三个入口共用同一列表方法，导出亦带级别；② 已作废/已打印行不标色（提醒只关心「已送达未验收」）；③ reminderLevel 仅对黄/红行赋值，无提醒行为 null，前端按 null 处理，避免与 0 语义混淆。 |

### W0-2.7 月结调整单「原订单关联摘要」✔（2026-08-31）

| 任务 | 结果 |
|---|---|
| 调查结论 | ✔ 蓝图 §2「月结调整追溯：原订单显示调整摘要并链接对应下月调整单，不改写原订单快照」；`t_month_adjustment` 仅有 customer_id+bill_month（无订单级外键），纠错本身是「客户+结算月」粒度。**结论：按蓝图口径做客户+结算月粒度摘要，不做订单级关联列/中间表（不过度设计）**；订单归月=该订单最近一张已提交验收单 `accept_date` 所在月（与 W0-3.1 归月口径一致），未验收归月的订单返回空摘要 |
| 后端 | ✔ 新增 `GET /month-adjustment/by-order/{saleOrderId}`（hasAnyPermi monthAdjustment:list / order:sale:query）返回 `OrderAdjustmentSummaryVO`（billMonth + 调整单列表含草稿 + 应收/成本调整合计）；`MonthAdjustmentService.selectBySaleOrderId`；`AcceptanceMapper.selectLatestAcceptDateBySaleOrderId`（acceptance join source_item 取 max(accept_date)）；不改写原订单快照、无 SQL 变更 |
| 前端 | ✔ 销售订单列表行新增「调整摘要」按钮（status≥2 已配送及以上可见）→ 对话框展示：结算月、调整单表（单号 TJ* / 草稿·已提交状态 / 应收调整 / 成本调整 / 备注）+ 合计行；未归月显示「尚未验收归月，暂无关联调整」提示；新增 `api/order/monthAdjustment.js` |
| 跳转说明 | ⏳ 蓝图要求的「链接跳转对应调整单」：调整单/月结管理前端页（W0-3.1 前端）尚未建设，暂无跳转目标，摘要已在对话框内完整展示；待月结/调整单页面落地后在摘要行补「去调整单页」跳转（已列入 W0-3.1 前端待办） |
| 测试 | ✔ 新增 `MonthAdjustmentServiceImplTest` 3 项（按归月聚合+合计+查询条件校验/未验收归月为空/订单不存在为空）；全工程 **262/262**；`npm run build:prod` 通过 |

> 设计取舍备注：① 摘要含草稿态调整单（标注状态），因草稿也是「原订单该月的纠错意图」，老板视角宜见；合计行同时给出含草稿口径；② 归月取「最近一张已提交验收单」，跨月补验收的多送货单极端场景以最新验收月为准，与月结冻结粒度（客户+月）一致，不逐月拆分展示。 |

### W0-4.4 模板复制约束对齐蓝图 ✔（2026-08-31）

| 任务 | 结果 |
|---|---|
| 约束落地 | ✔ `print/template/index.vue`：复制按钮按 bindType 收敛——仅全局默认（bindType=3）行显示可点「复制」（以全局模板为底稿创建未绑定草稿副本）；bindType=1/2（客户/客户+点）行复制按钮置灰，悬浮提示「客户模板不可作为底稿复制（蓝图§2：客户模板只允许从全局模板复制），请从全局默认模板复制」 |
| 决策说明 | 不放宽：客户模板常含客户定制版式/品牌元素，允许跨客户复制存在串用风险；新增/导入链路产出的新底稿本就是未绑定草稿（bindType=3），不影响效率；后端无需新增接口（复制=前端组合 add，绑定类型由创建者自选，本约束为工作流规则） |
| 验证 | ✔ `npm run build:prod` 通过；无后端改动 |

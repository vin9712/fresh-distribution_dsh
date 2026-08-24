# 生鲜配送管理系统——开发文档（DEVELOPMENT.md）

> 配套：`DESIGN.md`（权威设计）、`agent-development-brief.md`（任务切片协议）
> 本文将设计落地为可执行的数据模型、接口契约、页面规划与实施阶段。与 DESIGN.md 冲突时以 DESIGN.md 为准。

## 1. 工程基线

| 项 | 现状 |
|---|---|
| 后端 | `lin-entry`（启动）、`lin-admin`（web/controller）、`lin-common`（core）；RuoYi 3.8.8 改造，JDK 17，端口 8090（profile=local） |
| 前端 | `RuoYi-Vue3`（3.9.2，业务页面已全部迁移，vxe-table v4），dev 端口 1025，代理后端 8090 |
| 数据库 | MySQL 8，`../../sql/` 目录含 `ry_20240629.sql`（RuoYi 骨架）、`init_fresh_distribution.sql`（业务表）、`new_added_sql.sql`（菜单/字典增量） |
| 业务表 | 见 §2 清单 |
| 环境约定 | 见 `DEV-ENV-NOTES.md`（Node 版本、PowerShell 5.1 编码陷阱、E2E 工具 `.dsh-e2e/`） |

## 2. 数据模型

### 2.1 表清单与处理方式

**处理方式分三类**：

1. **原样沿用（不重做）**：商品分类、商品库（SPU）、商品信息（SKU）、客户信息——现有表、后端接口、已迁移的 Vue3 页面（含助记码、导入导出、级联选择）全部继续使用，仅在其上做"改造"列所列的增量。
2. **复用改造**：只改状态机/字段/语义，页面做对应适配。
3. **新建**：全新表 + 接口 + 页面。

| 表 | 处理 | 说明 |
|---|---|---|
| t_product_category / t_product_spu / t_product_sku / t_customer | **原样沿用** | 商品分类/商品库/商品信息/客户信息四模块不重做 |
| t_customer_dept | **复用改造** | 升级为配送点：页面/字段更名 `delivery_point` 语义，必要时加 `alias` 检索字段 |
| t_supplier / t_supplier_detail | **复用（轻量）** | 采购录单可选关联或直填名称 |
| t_product_sku_quote(+detail) | **复用改造** | 客户报价：加 `effective_date`/`expire_date`，保留状态流 |
| t_sale_order(+detail) | **复用改造** | 状态字段改五状态；加 `adjust_flag`；行快照字段核对（品名/规格/单位/单价/小计） |
| t_delivery_order(+detail) | **复用改造** | 加状态（待打印/已打印/已送达）、`print_count` |
| t_print_template / t_print_task | **按打印方案改造** | 选型确定后调整 JSON 格式与字段 |
| 以下为**新建表** | | |
| temp_product | 新建 | 临时商品（录单时无 SKU 即建） |
| product_alias | 新建 | 全局别名（名称/拼音/英文缩写 → SKU） |
| customer_sku_mapping | 新建 | 客户 SKU 映射（客户别名 → 我方 SKU） |
| price_template / price_template_sku | 新建 | 报价模板及模板 SKU 价（含有效期） |
| delivery_point_price | 新建 | 配送点报价（含有效期） |
| order_adjustment | 新建 | 加退换调整（关联原订单/原行、类型、原因、日期） |
| purchase_order / purchase_item | 新建 | 采购单（来源类型：自动/手工；来源订单 ID 列表；供应商；成本） |
| acceptance / acceptance_item | 新建 | 验收单（delivery_order_id 唯一；实收/损耗/金额） |

### 2.2 通用规范

- 主键 `BIGINT` AUTO_INCREMENT；关联键同类型；`DATETIME` 时间、`DATE` 业务日期；
- 数量/单价 `DECIMAL(10,2)`，金额 `DECIMAL(12,2)`，Java 侧 `BigDecimal`；
- 单号 `VARCHAR(32)` 唯一索引；软删/停用优先，已发生业务不物理删除；
- 建表脚本必须能从空库执行（`../../sql/` 目录维护一套最新基线 + 增量脚本）。

### 2.3 关键表字段概要（新建表）

**purchase_order**：id、code(PC...)、order_date、source_type(1自动/2手工)、source_order_ids(JSON)、supplier_id(**可空，确认时后补**)、supplier_name、total_amount、status(0草稿/1已确认/2已入库)、remark、审计字段。
**purchase_item**：id、purchase_id、sku_id、product_name/spec/unit 快照、quantity、unit_price、subtotal、sort。
**acceptance**：id、code(YS...)、delivery_order_id(UNIQUE)、customer_id、delivery_point_id、accept_date、total_amount、status(0草稿/1已提交)、审计字段。
**acceptance_item**：id、acceptance_id、delivery_item_id、sku 快照、delivered_quantity、actual_quantity（**可超送**）、unit_price、loss_quantity（**可为负，负值必填 loss_reason**）、actual_amount、sort。
**order_adjustment**：id、order_id、order_item_id(可空)、type(1加单/2退单/3换货)、reason、adjust_date、detail_json(换货时含加/退两行说明)、审计字段；**调整同时修改原订单行数量（或新增调整行），验收基线取调整后的订单行**。
**price_template**：id、name、status、effective_date、expire_date、is_default(全局默认)、remark。
**price_template_sku**：id、template_id、sku_id、unit_price、effective_date、expire_date。
**delivery_point_price**：id、delivery_point_id、sku_id、unit_price、effective_date、expire_date、remark。
**temp_product**：id、name、spec、unit、default_price、create_by。
**product_alias**：id、alias_type(1名称/2拼音/3英文缩写)、alias、sku_id。
**customer_sku_mapping**：id、customer_id、customer_alias、sku_id。

## 3. 后端模块与 API 契约

统一前缀 `/dev-api`（前端代理去前缀）；响应沿用 RuoYi `AjaxResult`/`TableDataInfo`；写接口防重复提交（幂等键/乐观锁）。

### 3.1 基础数据

| 接口 | 说明 |
|---|---|
| GET/POST/PUT/DELETE `/product/category/**` | 分类 CRUD（现状保留） |
| GET/POST/PUT/DELETE `/product/spu/**` | 商品 SPU CRUD + 导出 + **导入**（模板=导出模板，名称重复跳过） |
| GET/POST/PUT/DELETE `/product/sku/**` | 商品/SKU CRUD + 导入（现状已有） |
| GET/POST/PUT/DELETE `/partner/customer/**` | 客户 CRUD + 导入（现状已有） |
| GET/POST/PUT/DELETE `/delivery/point/**` | 配送点（原客户部门升级） |
| GET/POST/PUT/DELETE `/product/alias/**`、`/product/mapping/**` | 别名与客户 SKU 映射 |
| GET/POST `/product/temp/**` | 临时商品 |

### 3.2 报价管理

| 接口 | 说明 |
|---|---|
| GET/POST/PUT/DELETE `/price/customer/**` | 客户报价 CRUD（沿用现状 quote 流程 + 有效期） + **导入**（多客户多行按客户聚合生成报价单，同客户+SKU 已存在则新建，模板=导出模板） |
| GET/POST/PUT/DELETE `/price/template/**`、`/price/template/sku/**` | 报价模板与模板价 |
| GET/POST/PUT/DELETE `/price/point/**` | 配送点报价 |
| GET `/price/query?customerId=&deliveryPointId=&skuId=&deliveryDate=` | **取价接口**：返回优先级命中结果或空价 |

### 3.3 订单管理

| 接口 | 说明 |
|---|---|
| GET/POST/PUT `/order/sale/**` | 订单 CRUD（DRAFT 保存/确认=CONFIRMED；DRAFT 跨天保留） |
| POST `/order/sale/{id}/confirm` | 确认订单 |
| POST `/order/sale/{id}/recall` | 撤回确认（回 DRAFT；已生成送货单的订单拒绝撤回） |
| POST `/order/sale/{id}/settle` | 月结标记 SETTLED（管理员；之后禁止调整） |
| GET/POST `/order/adjustment/**` | 加退换调整（配送后场景；改订单行数量+记录原因） |
| GET `/order/sale/{id}` | 详情（含快照行与调整记录） |

### 3.4 采购管理

| 接口 | 说明 |
|---|---|
| POST `/purchase/generate?orderDate=` | 按配送日期汇总生成草稿（幂等：同日期重复调用不重复生成） |
| GET/POST/PUT `/purchase/**` | 采购单 CRUD（手工补货、改数量、填供应商/成本） |
| POST `/purchase/{id}/confirm`、`/purchase/{id}/stockin` | 确认、入库 |

### 3.5 配送与验收

| 接口 | 说明 |
|---|---|
| POST `/delivery/generate?deliveryDate=` | 按客户+配送点分组生成送货单（幂等） |
| GET `/delivery/**` | 送货单查询/详情 |
| POST `/delivery/{id}/print` | 记录打印次数 + 日志 |
| POST `/delivery/{id}/deliver` | 标记送达 → 订单 DELIVERED |
| POST `/acceptance/create/{deliveryId}` | 由送货单创建验收单（初始行=送货行基线；一单一验，重复创建返回 409） |
| POST `/acceptance/{id}/submit` | 提交：**后端重算**每行实收金额/损耗与整单总额 → 订单 ACCEPTED |
| GET `/acceptance/**` | 验收查询 |

### 3.6 报表与系统

| 接口 | 说明 |
|---|---|
| GET `/report/saleDaily?date=` | 销售日报（按配送日期，客户+配送点分组，导出 Excel） |
| GET `/report/customerStatement?customerId=&start=&end=` | 客户对账单（默认自然月，验收单明细，导出 Excel） |
| GET `/system/**` | RuoYi 原生（用户/角色/菜单/日志） |

## 4. 前端规划（RuoYi-Vue3 工程）

### 4.1 一级菜单（与 DB sys_menu 对应）

```text
工作台 / 基础数据 / 报价管理 / 订单管理 / 采购管理 / 配送管理 / 报表中心 / 系统管理
```

菜单与按钮按角色渲染（`v-hasPermi`），后端独立鉴权。

### 4.2 页面清单

| 页面 | 要点 |
|---|---|
| 工作台 `/workbench` | 待办卡片：待录订单、待确认、待生成采购、待打印送货、待验收、待处理加退换 |
| 订单编辑 `/order/sale/detail` | 先选客户/配送点/配送日期；vxe 表格连续录入；检索优先级=客户映射>别名>拼音>品名；后端取价，空价行内警示；行内小计；保存草稿（跨天保留）与确认；临时商品一键转正 |
| 采购单 `/purchase/index` | 手动汇总按钮/定时生成结果列表；自动单供应商留空、确认时后补；行编辑数量与成本；确认/入库 |
| 送货单 `/delivery/index` | 按配送日期/客户/配送点分组视图（客户+配送点+配送日一单）；明细按商品合并；**模板按客户+配送点组合/客户/全局默认三级选择**；模板预览；批量打印（逐张触发、份数=联数）；打印次数；标记送达 |
| 验收单 `/acceptance/index` | 送货数量只读、实收可编辑（**可超送**）；实时损耗/实收金额预览（负损耗填原因）；提交后订单 ACCEPTED |
| 报价页（客户/模板/配送点） | 沿用现状客户报价页 + 有效期字段；新增模板与配送点报价维护页；客户报价页加**导入**入口 |
| 商品库/商品信息/客户信息 | 现状沿用；商品库（SPU）补**导入**入口（SKU/客户已有） |
| 报表中心 | 销售日报、客户对账单（日期/客户筛选、导出） |
| 打印模板设计 | 按 §5 选型：hiprint 迁移（路线 A）或 JimuReport 集成（路线 B） |

### 4.3 前端复用资产

- vxe-table v4 可编辑表格（订单/采购/验收录入统一采用 sale/detail 已验证模式）；
- `dict.type` 字典 mixin（`src/plugins/dict.js`）供 Options API 页面使用；
- `pinyin-pro` 用于别名/助记码生成与检索。

## 5. 打印方案落地（已定：直接集成 JimuReport）

需求要点（DESIGN.md §9.1）：送货单多格式（客户+配送点组合绑定）、浏览器端设计与打印、多联、四类单据、常用报表设计器兼容。**用户已拍板：直接集成 JimuReport（不迁移 hiprint）。**

**模板数据模型（先行）**：

- `t_print_template` 增加 `render_engine`（`jimureport`）、`bind_type`（1 客户+配送点组合 / 2 客户 / 3 全局默认）、`customer_id`（可空）、`delivery_point_id`（可空）、`copies`（联数）、`is_default`；
- 模板选择：按送货单的客户+配送点依次匹配 组合绑定 → 客户绑定 → 全局默认；
- `template_json` 存 JimuReport 模板 JSON。

**实施步骤**：

0. **S0-3 技术验证（已完成，2026-08）**：结论通过。依赖 `org.jeecgframework.jimureport:jimureport-spring-boot3-starter-fastjson2:2.0.0`（Boot 3.4.x 系）；schema 见 `../../sql/s0_3_jimureport_init.sql`（17 张 jimu_* 核心表）；鉴权桥接 `JimuReportTokenServiceImpl`（实现 `JmReportTokenServiceI`，token 参数 + RuoYi JWT/Redis 校验）+ SecurityConfig 放行 `/jmreport/**`；已验证：JDK17 编译启动正常、`/jmreport/list?token=<JWT>` 返回完整设计器页面（509KB HTML）、无 token/伪造 token 均被拒。待用户实机验证：设计器交互、打印效果、社区版功能边界（水印等）。

1. **技术验证（S0-3，先行）**：引入 JimuReport 社区版依赖，验证 JDK 17 兼容性、在线报表/打印设计器、数据源配置（对接现有业务表/接口）、与 RuoYi JWT 鉴权集成、多联与浏览器打印能力；产出验证报告与集成方案；
2. 前端嵌入 JimuReport Vue3 组件（打印模板菜单替换占位页）；
3. 送货单模板设计与打印流程（选模板→渲染→打印→`/delivery/{id}/print` 记录次数）；
4. 验收单、采购单、报价单模板；
5. 销售日报与客户对账用 JimuReport 设计并嵌入报表中心；
6. 存量 hiprint（vue2 PrintModule 与模板 JSON）保留作参考，不迁移。

**回退预案**：若社区版打印能力不满足（套打/多联），回退 hiprint 迁移方案（数据模型已引擎中性）。

## 6. 实施阶段（纵向切片，一次一个可独立验证）

| 阶段 | 内容 | 验收要点 |
|---|---|---|
| P0 基座 | 权限菜单调整（文员/管理员）、五状态字典、单号服务、新建表脚本 | 空库可执行；单号并发唯一测试 |
| P1 基础数据 | 配送点升级（客户部门→配送点）、别名、客户 SKU 映射、临时商品 | 页面与接口联调 |
| P2 报价 | 客户报价加有效期；模板、配送点报价；取价接口 | 取价优先级自动化测试（§DESIGN 验收 1） |
| P3 订单 | 五状态改造、工作台、订单编辑（取价接入/快照/检索/草稿）、加退换 | 录单→确认 E2E；快照不回写测试 |
| P4 采购 | 采购单生成（定时+手动幂等）、手工补货、成本、状态 | 23:00 汇总不重复；状态与订单解耦测试 |
| P5 配送+验收 | 送货单生成/三状态/打印、验收单与后端重算 | 一单一验、损耗/金额重算、E2E |
| P6 报表+打印+上线 | 销售日报、客户对账；打印方案落地；部署（Nginx/HTTPS/备份） | 报表口径、打印计数、部署演练 |

## 7. 测试与验收

- 后端：JUnit 覆盖 DESIGN.md §12 验收标准 1–14 中的后端项；
- 前端：`.dsh-e2e/` 无头浏览器回归（登录→各菜单→弹窗→详情页交互，零 console 错误）；
- 完整 E2E：录单→采购→送货→打印→验收→对账手工走查脚本；
- 每个功能完成必须实际执行测试并报告命令、退出状态、未验证项与风险（见 agent-development-brief.md）。

## 8. 部署（国内云 Linux）

- 基线：2C4G、JDK 17、MySQL 8、Nginx 1.24+；
- Nginx：HTTPS、托管前端 dist、`/prod-api` 反代 8080；
- 数据备份：每日全量 mysqldump 保留 7 天，月备份保留 1 年；操作日志保留 5 年按季度归档；
- 上线检查：登录锁定、权限、HTTPS、数据库恢复演练。

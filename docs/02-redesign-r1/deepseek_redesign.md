# 生鲜配送 ERP — 基础信息模块设计文档（重构版）

> **实现状态（2026-08-21）：✅ 已全部落地**。数据层/服务层/接口层/前端均完成并通过验证，
> 详见 `r1_redesign_progress.md`（执行进度与全链路验证）与 `../../sql/r1_basicinfo_redesign.sql`/`../../sql/r1_frontend_menu.sql`。
> 下表为落地对照：
>
> | 章节 | 落地情况 |
> |---|---|
> | §3 核心表结构 | 全部建表（`../../sql/r1_basicinfo_redesign.sql` 幂等）；标准 SKU 已重建，旧 SKU 数据清空待重录 |
> | §4 编码规则 | SKU `S+8位`、客户商品 `C{客户ID}+6位` 已实现（`BizCodeService.nextSkuCode/nextCustomerSkuCode`） |
> | §5.1 批量赋值默认 SKU | 已实现（模板 CRUD + `batchAssign` 三策略） |
> | §5.2 客户商品查询与配送点覆盖 | 已实现（`listCustomerProducts` 合并覆盖：隐藏/别名/价格；关键字检索含覆盖别名） |
> | §5.3 取价引擎 | 已实现（覆盖 > 客户报价 > 模板） |
> | §5.4 临时商品处理 | 已实现（客户专用/全局、转正自动建/复用标准SKU+入客户池、showAll 列表开关） |
> | §5.5 非标称重商品 | 字段已落地（is_weighted），订单侧称重逻辑沿用原系统 |
> | §6 接口规划 | 全部就绪（标准SKU/客户商品/配送点覆盖/模板/临时商品/取价） |
> | §9 后续扩展 | 库存/采购/单位换算/价格历史 未实现（符合“不过度设计”） |
>
> 正文为设计基线，具体实现可按开发进度微调（已微调处：`delivery_sku_override` 唯一键采用 `(delivery_point_id, sku_id)` 单版本覆盖，未按生效日期分区）。

---

## 1. 设计目标

- 明确 SPU、SKU、客户商品三层职责，消除 SKU 与客户强耦合。
- 建立客户级商品池，支持批量赋值默认 SKU 及客户个性化修改。
- 保留现有报价体系，并理顺配送点覆盖逻辑。
- 支持临时商品快速录单与转正流程。
- 为未来库存、采购、报表等模块预留扩展空间。

---

## 2. 整体架构
商品体系
├── 商品分类 category
├── 商品库 SPU
├── 标准 SKU（不含客户）
├── 客户商品 customers_sku（客户对 SKU 的个性化：别名、编码、起订量等）
├── 商品别名 product_alias（全局别名）
└── 临时商品 temp_product（快速录单，可转正）

客户体系
├── 客户 customer
├── 客户分组 customer_group
└── 客户配送点 customer_delivery（支持树形父子结构）

报价体系
├── 客户报价单（t_product_sku_quote + detail）—— 客户级批量价格调整
├── 配送点报价/覆盖 delivery_sku_override —— 配送点级价格与别名覆盖
├── 报价模板 price_template —— 客户分组或默认价格模板
└── 取价引擎 PriceQueryService
优先级：配送点覆盖 > 客户报价单 > 报价模板

辅助支撑
├── 客户商品映射 customer_sku_mapping（可选，用于搜索）
└── 通用编号序列 biz_code_seq


---

## 3. 核心表结构

### 3.1 商品分类 `t_product_category`

保留现状，无需改动。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(200) | 分类名称，唯一 |
| parent_id | BIGINT | 上级分类ID，0=顶级 |
| code | CHAR(10) | 分类编码，编码体现层级 |
| level | TINYINT | 分类级别 |
| sort | INT | 排序 |
| is_deleted | TINYINT | 逻辑删除 |
| create_by/create_time | — | 审计字段 |
| update_by/update_time | — | 审计字段 |
| remark | VARCHAR(500) | 备注 |

### 3.2 商品库 SPU `t_product_spu`

保留现状，但移除自动创建默认 SKU 的逻辑（转为手动或独立功能）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| category_id | BIGINT | 所属分类 |
| name | VARCHAR(200) | 商品名称 |
| description | VARCHAR(200) | 商品描述 |
| mnemonic_code | VARCHAR(128) | 助记码 |
| images | JSON | 商品图片 |
| saleable | TINYINT | 是否上架 |
| sort | INT | 排序 |
| valid | TINYINT | 是否有效 |
| is_deleted | TINYINT | 逻辑删除 |
| ... | — | 审计字段 + remark |

### 3.3 标准 SKU `t_product_sku`

**关键变化**：移除 `customer_id` 字段，SKU 成为纯粹的标准商品单元。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| spu_id | BIGINT | 所属 SPU（可空） |
| category_id | BIGINT | 分类ID |
| code | VARCHAR(32) | **全局唯一编码**，纯数字自增（见编码规则） |
| name | VARCHAR(200) | 商品名称（默认从 SPU 继承） |
| spec_name | VARCHAR(200) | 规格描述，如“大果”“5斤/箱” |
| unit | VARCHAR(20) | 固定单位，如“箱”“斤” |
| is_weighted | TINYINT | 是否称重商品（1=称重，如散装菜；0=非称重，如箱装） |
| base_unit | VARCHAR(20) | 可选，用于跨 SKU 汇总的基础单位 |
| conversion_rate | DECIMAL | 可选，与基础单位的换算率 |
| sale_price | DECIMAL(10,2) | 参考售价（仅展示，非交易价格） |
| saleable | TINYINT | 是否上架 |
| valid | TINYINT | 是否有效 |
| is_deleted | TINYINT | 逻辑删除 |
| ... | — | 审计字段 + remark |

> 唯一约束：`(spu_id, spec_name, unit)` 或 `(category_id, name, spec_name, unit)`  
> `code` 为全局唯一，采用自增序列（如 `10001`, `10002`），不与客户信息耦合。

### 3.4 客户 `t_customer`

保留现状，增加客户分组支持。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(200) | 客户名称，唯一 |
| alias | VARCHAR(200) | 客户别名 |
| group_id | BIGINT | 客户分组ID（关联 customer_group） |
| type | VARCHAR(10) | 客户类型（字典） |
| tel | CHAR(11) | 手机号 |
| address | VARCHAR(200) | 地址 |
| valid | TINYINT | 是否有效 |
| is_deleted | TINYINT | 逻辑删除 |
| ... | — | 审计字段 + remark |

### 3.5 客户分组 `customer_group`

新增表，用于默认模板按分组适配。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(50) | 分组名称（如“批发”“食堂”） |
| is_deleted | TINYINT | 逻辑删除 |

### 3.6 客户配送点 `t_customer_dept`

保留现状，支持父子层级，顶级为默认配送点（parent_id=0）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| customer_id | BIGINT | 客户ID |
| parent_id | BIGINT | 上级配送点ID，0=顶级/默认 |
| code | VARCHAR(200) | 配送点编号（见编码规则） |
| name | VARCHAR(200) | 部门名称 |
| mnemonic_code | VARCHAR(128) | 助记码 |
| address | VARCHAR(200) | 配送地址 |
| location | GEOMETRY | 位置坐标（可空） |
| valid | TINYINT | 是否有效 |
| is_deleted | TINYINT | 逻辑删除 |
| ... | — | 审计字段 + remark |

### 3.7 客户商品 `customers_sku`

**新增核心表**，承载客户对标准 SKU 的个性化信息及商品池关系。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| customer_id | BIGINT | 客户ID |
| sku_id | BIGINT | 标准 SKU ID |
| alias | VARCHAR(200) | 客户自定义商品别名（可空） |
| customer_code | VARCHAR(64) | 客户商品编码（全局唯一，见编码规则） |
| unit | VARCHAR(20) | 客户下单单位（默认与 SKU 单位一致，一般不允许修改） |
| min_order_qty | DECIMAL(10,2) | 最小起订量，默认 1 |
| order_step | DECIMAL(10,2) | 下单步长（如整箱=1箱） |
| is_follow_default | TINYINT | 是否跟随默认模板（1=是，0=已个性化） |
| source_template_id | BIGINT | 来源模板ID（可空） |
| status | TINYINT | 1可用 0停用 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

> 唯一约束：`(customer_id, sku_id)`  
> `customer_code` 唯一，用于快速检索。

### 3.8 配送点商品覆盖 `delivery_sku_override`

**整合原配送点报价表**，统一管理配送点级的价格和别名覆盖，并可控制可见性。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| delivery_point_id | BIGINT | 配送点ID（关联 t_customer_dept.id） |
| sku_id | BIGINT | 标准 SKU ID |
| is_available | TINYINT | 是否可用（1=可见，0=隐藏） |
| price_override | DECIMAL(10,2) | 价格覆盖，可空（空则继承客户级价格） |
| alias_override | VARCHAR(200) | 别名覆盖，可空 |
| effective_date | DATE | 生效日期，可空 |
| expire_date | DATE | 失效日期，可空 |
| created_at | DATETIME | 创建时间 |

> 唯一约束：`(delivery_point_id, sku_id, effective_date)` 或 `(delivery_point_id, sku_id)` 按生效日期区分。

### 3.9 客户报价单 `t_product_sku_quote` 及明细

保留现状，但 `sku_id` 改为指向标准 SKU。

**报价单头**：字段基本不变。  
**报价明细 `t_product_sku_quote_detail`**：`sku_id` 关联 `t_product_sku.id`，其余字段保留。

> 用途：客户级批量调价，是价格引擎的第二优先级。

### 3.10 报价模板 `price_template`、模板 SKU `price_template_sku`、模板客户绑定 `price_template_customer`

保留现状，`sku_id` 同样指向标准 SKU。模板作为兜底价格来源。

### 3.11 商品全局别名 `product_alias`

保留现状，关联标准 SKU，用于搜索增强。

| 字段 | 说明 |
|------|------|
| id | 主键 |
| alias_type | 1=名称别名, 2=拼音, 3=英文缩写 |
| alias | 别名内容 |
| sku_id | 关联 SKU |

### 3.12 临时商品 `temp_product`

保留并加强，支持快速录单和转正。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(200) | 临时商品名称 |
| spec | VARCHAR(200) | 规格 |
| unit | VARCHAR(20) | 单位 |
| default_price | DECIMAL(10,2) | 默认单价 |
| remark | VARCHAR(500) | 备注 |
| customer_id | BIGINT | **新增**：关联客户（可空，用于客户专用临时商品） |
| converted_sku_id | BIGINT | 转正后 SKU ID（可空） |

### 3.13 通用编号序列 `biz_code_seq`

保留，用于生成各种编号。

| 字段 | 说明 |
|------|------|
| biz_key | 业务键（如 sku_code, customer_sku_code） |
| seq | 当前序号 |

---

## 4. 编码规则

| 对象 | 规则 | 示例 |
|------|------|------|
| 标准 SKU | 全局自增序号，格式 `S` + 8位数字 | `S00000001` |
| 客户商品编码 | `C{客户ID}` + 6位自增序号 | `C1001000001` |
| 配送点编码 | 保持现状（父级：助记码+客户ID+00000，子级：父助记码+客户ID+5位序号） | `ABC00123400001` |
| 报价单号 | `BJ` + yyyyMMdd + 5位序号 | `BJ2025061300001` |
| 分类编码 | 保持现状 | `110001` |

---

## 5. 关键流程设计

### 5.1 批量赋值默认 SKU

默认模板（`default_sku_template`）本质是一组标准 SKU 的集合，不包含价格，用于快速将一组商品分配给多个客户。

**表设计（新增）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(100) | 模板名称，如“食堂常用商品” |
| customer_group_id | BIGINT | 适用客户分组（可空） |
| status | TINYINT | 1启用 0停用 |
| created_at | DATETIME | 创建时间 |

模板明细表 `default_sku_template_item`：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| template_id | BIGINT | 模板ID |
| sku_id | BIGINT | 标准 SKU ID |

**批量赋值流程**：

1. 选择模板或直接勾选一批 SKU。
2. 选择目标客户范围（按分组或手动选择）。
3. 选择覆盖策略：
   - **仅新增**：只为没有该 SKU 的客户创建客户商品。
   - **覆盖未个性化**：为未个性化（`is_follow_default=1`）的客户更新商品信息（如别名、起订量等）。
   - **全部覆盖**：强制覆盖所有客户，慎用。
4. 系统遍历目标客户，对每个 SKU 判断：
   - 不存在则插入 `customers_sku`（`is_follow_default=1`，`source_template_id=当前模板`）。
   - 存在则根据覆盖策略决定是否更新。
5. 价格不参与模板，客户商品的价格由报价体系决定，初始可无价格，取价时回退到模板或报价单。

### 5.2 客户商品查询与配送点覆盖

前端查询某配送点的可用商品列表时，需要合并客户级商品池和配送点覆盖信息。

**逻辑**：

1. 查客户商品表 `customers_sku` 获取客户拥有的 SKU 集合及基本信息。
2. 左连接 `delivery_sku_override`，按配送点ID和 SKU ID 取覆盖信息。
3. 若覆盖记录存在：
   - 若 `is_available=0`，则从列表中移除该 SKU。
   - 否则，用 `price_override` 和 `alias_override` 覆盖客户级的显示价格和别名。
4. 若覆盖记录不存在，则显示客户级信息，价格通过取价引擎获取。

### 5.3 取价引擎

**优先级**：

1. **配送点覆盖价格**（`delivery_sku_override.price_override`）
2. **客户报价单**（已发布且有效的报价明细）
3. **报价模板**（客户绑定的模板或全局默认模板）

**实现要点**：

- 所有价格查询均传入 `customerId`、`deliveryPointId`、`skuId`、`deliveryDate`。
- 依次查询，命中即返回。
- 未命中返回空价，由录单界面手动处理或提示。

### 5.4 临时商品处理

**下单时**：

- 客户商品列表接口返回客户商品池 + 临时商品（`temp_product` 中关联当前客户或全局临时商品），并标记 `isTemp=true`。
- 业务员也可手动输入新临时商品，系统自动保存到 `temp_product` 表并关联当前客户。
- 订单行标记 `is_temporary=1`，并存储商品快照。

**转正**：

- 业务员在临时商品管理界面或订单详情中点击“转正”。
- 系统创建标准 SKU（单位、规格取自临时商品），并创建 `customers_sku` 关联当前客户。
- 若标准 SKU 已存在（相同名称+规格+单位），则直接关联。
- 更新 `temp_product.converted_sku_id`，标记已转正。

### 5.5 非标称重商品

- SKU 的 `is_weighted=1` 表示称重商品。
- 订单行字段包含：
  - `plan_qty`：客户下单数量（允许小数，如 10.5 斤）
  - `actual_qty`：实际分拣重量（可修改，允许小数）
- 结算金额 = `actual_qty × 单价`。
- 称重商品单位固定为斤/公斤，不进行单位换算。
- 箱装等固定包装商品 `is_weighted=0`，按整数箱下单，不记录实际重量，统计按箱。

### 5.6 库存与采购（预留）

- 当前 JIT 模式库存少，暂不设计库存表。
- 采购时直接按标准 SKU 下单，单位一致。
- 未来可增加库存变动流水表 `inventory_transaction`，记录采购入库、销售出库、盘点等操作，关联 SKU 和数量。

---

## 6. 接口规划

| 模块 | 主要接口 | 说明 |
|------|----------|------|
| 商品分类 | `/product/category/**` | 保持现状 |
| SPU | `/product/spu/**` | 保持现状，移除自动建 SKU 逻辑 |
| 标准 SKU | `/product/sku/**` | 调整：无 `customerId` 参数，新增全局唯一编码 |
| 客户商品 | `/product/customer-sku/**` | 新增：客户商品分页、批量赋值、个性化修改 |
| 配送点覆盖 | `/price/delivery-override/**` | 替代原配送点报价接口，支持价格和别名覆盖 |
| 报价单 | `/product/quote/**` | 保持现状，`skuId` 指向标准 SKU |
| 报价模板 | `/price/template/**` | 保持现状 |
| 临时商品 | `/product/temp/**` | 增强：支持关联客户、转正后自动关联 |
| 取价引擎 | `/price/query` | 不变，内部逻辑调整 |

---

## 7. 数据初始化与迁移策略

由于项目尚未上线，可完全采用新表结构，无需数据迁移。但需注意：

- 清除旧表中 `customer_id` 相关的 SKU 数据，重新录入标准 SKU。
- 重新设计客户商品池的初始化流程。
- 报价单、报价模板等历史数据可清空或按新结构重新录入。

---

## 8. 开发注意事项

1. **客户商品唯一性**：确保 `(customer_id, sku_id)` 唯一，避免重复。
2. **配送点覆盖查询**：在服务层封装统一方法，避免各处重复 join。
3. **编码生成**：使用 `biz_code_seq` 原子自增，确保并发安全。
4. **临时商品转正**：注意事务处理，同时创建 SKU 和客户商品。
5. **价格历史**：客户报价单和配送点覆盖若需要历史，应保留生效日期，取价时取最新有效。
6. **搜索优化**：客户商品列表搜索应同时匹配标准 SKU 名称、别名、客户别名等，可用 ES 或缓存优化。

---

## 9. 后续扩展方向

- 库存管理：引入仓库、库存表、库存流水。
- 采购管理：基于标准 SKU 生成采购单。
- 多单位换算：若未来需要跨单位下单和库存转换，可增加单位换算表。
- 价格历史追溯：完整记录每次调价操作。

---

> 本文档为重构设计基线，具体实现可根据实际开发进度微调。
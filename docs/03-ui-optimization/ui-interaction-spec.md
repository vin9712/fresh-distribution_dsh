# 生鲜配送 ERP — 模块 UI 交互规格（基于现有代码现状）

> 生成日期：2026-08-22
> **最近更新：2026-08-25（v1.2 商品资料效率包）**——菜单更名（2018 商品信息→商品规格、order_num 重排见 `sql/s9_menu_product_ia.sql`）；商品库/客户商品/报价新增粘贴导入向导；全局弹窗可拖拽居中；搜索表单 label-width 统一 80px。详细变更见《操作手册》迭代总览 `[v1.2]`，本文件正文未逐节重写。
> 组织方式：按 `sys_menu` 菜单树逐模块描述，与 `RuoYi-Vue3/src/views` 实际代码一一对应
> 相关文档：`../02-redesign-r1/deepseek_ui_redesign.md`（交互设计原则）、`../02-redesign-r1/r1_redesign_progress.md`（改造进度）
> 前端范围：**仅 `RuoYi-Vue3`**（Vue3 + Element Plus 2.13 + vxe-table 4.20），`ruoyi-ui` 为 Vue2 旧前端，已停止维护

---

## 0. 菜单树与页面映射

菜单数据来源：`../../sql/init_all.sql`（基线）+ `../../sql/s*_*_menu.sql`（各切片）+ `../../sql/r1_frontend_menu.sql`（R1 阶段）。

| menu_id | 菜单 | parent | path | component | 表格实现 |
|---------|------|--------|------|-----------|----------|
| 2073 | 工作台 | 0 | workbench | `workbench/index` | 卡片，无表格 |
| **4** | **基础信息**（目录 M） | 0 | basicInfo | — | — |
| 2012 | 客户信息 | 4 | customer | `partner/customer/index` | el-table |
| 2000 | 商品分类 | 4 | category | `product/category/index` | el-table（树形） |
| 2006 | 商品库(SPU) | 4 | spu | `product/spu/index` | **QuickTable** |
| 2018 | 商品规格(标准SKU) `[v1.2]` 更名 | 4 | sku | `product/sku/index` | **QuickTable** |
| 2090 | 客户商品 | 4 | customerSku | `product/customerSku/index` | **QuickTable** |
| 2050 | 别名与映射 | 4 | aliasMapping | `product/aliasMapping/index` | **QuickTable ×3 tab** |
| ~~2063~~ | ~~报价模板~~ 已下线 `[2026-08 W0-1]` | 4 | priceTemplate | `price/template/index` | el-table |
| 2064 | 配送点覆盖 | 4 | pointPrice | `price/pointPrice/index` | **QuickTable** |
| 2024 | 商品报价 | 4 | quote | `product/quote/index` | **QuickTable** |
| 2095 | 默认SKU模板 | 4 | defaultSkuTemplate | `product/defaultSkuTemplate/index` | **QuickTable** |
| **5** | **单据管理**（目录 M） | 0 | order | — | — |
| 2030 | 销售订单 | 5 | sale | `order/sale/index` | el-table + vxe 批量下拉 |
| 2036 | 送货单据 | 5 | delivery | `order/delivery/index` | el-table |
| 2081 | 验收单 | 5 | acceptance | `order/acceptance/index` | el-table |
| **6** | **打印管理**（目录 M） | 0 | print | — | — |
| 2042 | 打印模板 | 6 | template | `print/template/index` | el-table |
| 2075 | 采购管理 | 0 | purchase | `purchase/index` | el-table |
| 2087 | 报表中心 | 0 | report | `report/index` | el-table ×2 tab |

**隐藏路由**（`router/index.js` dynamicRoutes，不出现在菜单）：

| 路径 | 组件 | activeMenu | 入口 |
|------|------|-----------|------|
| `/basicInfo/customer-dept/index/:customerId` | `partner/customer/dept` | /basicInfo/customer | 客户列表点客户名 |
| `/basicInfo/quote-detail/index/:customerId` | `product/quote/detail` | /basicInfo/quote | 报价列表点"查看" |
| `/order/sale-detail/index/` | `order/sale/detail` | /order/sale | 订单列表"新增明细"/双击行 |

---

## 1. 两套交互范式（重要）

代码里存在**两种列表页范式**，新页面统一走范式 A。

### 范式 A：QuickTable（7 页已接入）

组件：`components/QuickTable/index.vue` + `quickTableMixin.js`，样式 `assets/styles/quick-table.scss`（main.js 全局引入 + 全局注册）。

统一交互契约：

| 交互 | 行为 | 实现 |
|------|------|------|
| `F2` | 触发 `@add` → 打开新增对话框 | window keydown |
| `F3` | 聚焦搜索区第一个 input | `focusSearch()` |
| `Delete` | 选中行时触发 `@delete(ids)` | 输入框内不响应 |
| `Ctrl+S` | 触发 `@save` | 列表页多数未接 |
| `Ctrl+K` | 触发 `@global-search` | **预留，无消费方** |
| `Esc` | 清除勾选 | `clearSelection()` |
| 双击行 | 触发 `@edit(row)` → 打开编辑对话框 | `cell-dblclick` |
| 右键行 | 菜单：新增/修改 ｜ 删除/刷新 | vxe v4 `menu-config.body.options` |
| 右键表头 | 菜单：刷新/重置列设置 | `menu-config.header.options` |
| 勾选行 | 底部浮出批量操作条（`batch-actions` 为空则只显计数） | transition 动画 |
| 列设置 | 显隐/拖拽调序/固定/宽度，localStorage `VXE_CUSTOM_STORE` | 需 grid `id` + 每列 `field` |
| 搜索区 | 右上箭头折叠，`v-model:showSearch` 双向 | — |
| 工具栏 | 自定义列 / 全屏 / 刷新 / 快捷键提示（?图标） | `toolbar-config` |

> **未保存离开守卫（2026-09-15）**：含未保存编辑的页面（订单录入 `order/sale/detail`、采购录入 `purchase/day`）必须**双保险**防丢数据：
> ① 路由级 `beforeRouteLeave`（浏览器/标签返回、切菜单、切 Tab、全局搜索跳转）脏则 `$modal.confirm`，取消用 `next(false)` 留在原页（Vue Router 会自动回退 URL）；
> ② 原生 `beforeunload` 脏则 `e.preventDefault()`，弹浏览器「离开此网站？」确认；
> ③ 页面自身「返回」按钮若已弹过确认，先置 `_allowLeave = true` 再跳，避免二次弹窗；脏判定用**完整脏检查**（含表头字段，不只明细）。
| 大数据 | >100 行自动虚拟滚动 | `scroll-y.gt=100` |

页面侧固定写法：`columns` 数组（`slots.default` 指向页面同名插槽）+ `batchActions` 数组 + mixin 提供的 `ids/single/multiple/handleSelectionChange/handleQuickDelete/handleQuickBatchAction`。

### 范式 B：原始 el-table（其余 10 页）

`el-form` 内联搜索 + `el-row` 按钮组 + `right-toolbar` + `el-table` + `pagination`。无快捷键、无右键菜单、无列设置。

`商品分类 / 客户信息 / 配送点` 三页曾接入 QuickTable，后按需**还原**为 el-table（见 commit 07a6325）。

---

## 2. 工作台（2073）

`workbench/index.vue` — 5 张统计卡片，点击跳对应列表页。

| 卡片 | key | 跳转 |
|------|-----|------|
| 待录/待确认订单 | draftOrders | /order/sale |
| 待生成采购单 | pendingPurchase | /order/sale |
| 待打印送货单 | pendingPrint | /order/delivery |
| 待验收 | pendingAcceptance | /order/delivery |
| 待处理加退换 | pendingAdjust | /order/sale |

数据源 `GET /workbench/summary`。交互仅 hover 抬升 + 点击跳转，无筛选、无自动刷新。

---

## 3. 基础信息（menu 4）

### 3.1 商品分类（2000）— el-table 树形

- 树形表格 `row-key="id"`，工具栏"展开/折叠"按钮切 `isExpandAll` + `refreshTable` 重挂载
- 操作列：新增子类（带 parentId 预填）/ 修改 / 删除
- 对话框字段：上级分类（treeselect）、分类名称、分类排序、备注
- 无 seq 列、无批量操作（仅工具栏批量删除按 `multiple` 禁用）

### 3.2 商品库 SPU（2006）— QuickTable

- 搜索：商品名称、商品分类（cascader）、上架、有效
- 列：id / 商品分类 / 商品名称(fixed) / 描述 / 上架 / 有效 / 备注 / 操作(fixed right)
- 批量条：删除
- 对话框字段：分类、名称、描述、助记码（`pinyin-pro` 自动生成）、是否上架、是否有效、备注
- 额外：Excel 导入（`product:spu:import`）、导出

### 3.3 客户信息（2012）— el-table

- 搜索：客户名称、是否有效、客户类别
- 列：客户编号 / 客户名称（**router-link 跳配送点页**）/ 别名 / 类型 / 有效 / 备注 / 操作
- 对话框字段：名称、别名、手机号、地址、是否有效、备注
- 导入 + 导出

**子页：配送点（隐藏路由 `dept.vue`）**
- 顶部"当前客户"选择器（切换客户即换列表）
- 列：编号 / 配送点 / 有效 / 备注 / 操作
- 对话框含助记码（拼音自动生成）
- 删除时提示 deptCodes

### 3.4 商品信息 / 标准 SKU（2018）— QuickTable

R1 重构后**已去客户维度**，此页只管全局标准 SKU。

- 搜索：商品分类、商品名称、上架、有效、匹配（是否已关联 SPU）
- 列：商品编号(S+8位，后端生成) / 分类 / 名称(fixed) / 规格 / 单位 / 称重 / 基础单位 / 换算率 / **参考售价（titleHelp 提示"仅展示，非交易价格"）** / 上架 / 有效 / 操作
- 批量条：**批量关联**（关联 SPU）+ 删除
- 操作列"更多"下拉：取消关联（仅 `row.spuId` 存在时显示）
- 对话框：分类、名称、助记码、规格、单位（字典 `t_sku_unit`）、是否称重（switch）、基础单位、换算率、参考售价、上架、有效

### 3.5 客户商品（2090）— QuickTable

客户商品池，**客户为必选筛选项**（切换客户即重查）。无分页。

- 搜索：客户（必选）、关键字、状态
- 列：客户商品编码(C{客户ID}+6位) / 别名 / 标准SKU(fixed) / 标准编码 / 规格 / 单位 / 起订量 / 步长 / 状态 / **个性化（isFollowDefault）** / 操作
- 批量条：**停用/启用** + 删除
- 三个对话框：新增（选客户+SKU，已分配 SKU 前端过滤）/ 个性化修改（PUT 自动置 `is_follow_default=0`）/ **批量赋值**
- 批量赋值弹窗流程：选模板或手动勾 SKU → 多选客户 → 选策略（1 仅新增 / 2 覆盖未个性化 / 3 全部覆盖）→ `POST /product/customer-sku/assign`

### 3.6 默认 SKU 模板（2095）— QuickTable

- 无分页。列：模板名称(fixed) / SKU数量 / 状态 / 创建时间 / 操作
- 编辑时通过 `GET /product/default-sku-template/{id}/items` 回显 SKU 明细
- 模板**不含价格**，仅决定"哪些 SKU 进客户池"

### 3.7 商品报价（2024）— QuickTable

- 搜索：报价客户、报价编号、有效、报价时间范围、生效时间范围（range 双向映射 start/end 字段）
- 列：客户 / 报价编号 / 生效时间 / 结束时间 / 状态 / 有效 / 创建时间 / 备注 / 操作（宽 250）
- 操作列状态机（字典 `t_sku_quote_status`）：
  - 状态=新增(0) → 显示「发布」
  - 状态=发布(1) 且 valid=0 → 显示「取消」下拉：撤销 / 失效
  - 恒显示：查看（跳报价详情页）/ 复制
- 批量条：删除
- 导入（`product:quote:import`）

**子页：报价详情（隐藏路由 `quote/detail.vue`）** — vxe-table 单元格编辑，按客户商品池组装报价草稿。

### 3.8 别名与映射（2050）— 三 tab，各自 QuickTable

`el-tabs type="border-card"`，**三个 QuickTable 均设 `:shortcuts="false"`** 避免快捷键互抢。

| tab | 内容 | 特殊交互 |
|-----|------|---------|
| 全局别名 | 别名 → SKU 映射 | 别名类型、关联SKU |
| 客户SKU映射 | 客户叫法 → 我方SKU | 按客户维度 |
| 临时商品 | 未转正/已转正商品 | **转正**按钮（`convertedSkuId != null` 时禁用）；`showAll=true` 查看全部 |

转正对话框：归属客户 + 商品分类级联 + 助记码 → `POST /product/temp/{id}/convert`（客户/分类必填），事务内查重→建/复用标准SKU→建客户商品池记录。

### 3.9 报价模板（2063）— el-table

> ⚠️ **已下线 `[2026-08 W0-1]`**：价格层级简化后订单仅使用客户正式报价，本菜单及后端接口/表已删除（`sql/w01_price_simplify.sql`）。历史交互描述仅存档。

- 操作列 4 个功能入口：设为默认 / **SKU价格**（子弹窗，可增删行编辑单价+有效期）/ **绑定客户**（子弹窗多选）/ 修改 / 删除
- 状态用本地 `statusOptions`，非字典

### 3.10 配送点覆盖（2064）— QuickTable

> ⚠️ **已下线**：`[2026-02]` 菜单已随 s11 删除；`[2026-08 W0-1]` 遗留表 delivery_point_price 亦已删除（价格层级简化，仅保留客户商品池「限定配送点」）。历史描述仅存档。

取代原"配送点报价"，接口 `/price/delivery-override/**`。无分页。

- 列：配送点(fixed) / 商品 / 是否可见 / 价格覆盖 / 别名覆盖 / 有效期 / 操作
- **新增即 upsert**：同配送点+SKU 已存在则更新
- ~~三层取价优先级：配送点覆盖 > 客户报价 > 报价模板~~（已废弃，现行：仅客户正式报价单一口径）

---

## 4. 单据管理（menu 5）

### 4.1 销售订单（2030）— el-table + vxe 批量下拉

- 搜索：送货单位(cascader) / 订单编号 / 来源 / 类型 / 状态 / 配送日期
- 工具栏：新增明细（跳详情页）+ **「批量处理」vxe-button 下拉**（批量确认 / 批量验收 / 批量月结 / 批量还原 / 批量删除，均按 `multiple` 禁用）+ 生成采购单 + 生成送货单
- 列：送货单位 / 订单编号 / 来源 / 类型 / 总金额 / 状态 / 配送日期 / 备注 / 操作
- **双击行**跳订单详情
- 操作列按状态显隐：
  - status=1(已确认) → 撤回
  - status=2或3(已配送/已验收) → 调整（加退换弹窗）
  - 恒显示：修改 / 删除
- 状态机（字典 `t_sale_order_status`）：`0草稿 → 1已确认 → 2已配送 → 3已验收 → 4已结算`

⚠️ `handleBuildPurchase` 仅做了状态校验后即结束（TODO：el-drawer 未实现）；`handleBuildDelivery` 为空函数。

**子页：订单详情（`sale/detail.vue`，1342 行，录单核心页）**

左 16 栏做单区 + 右 8 栏最近订单区：

- 订单表单：送货单位 cascader（有明细时禁改）/ 配送日期 / 订单编号（只读，可点刷新重取）/ 备注 / **自动新增开关**
- vxe-table 全键盘配置：`isArrow/isDel/isEnter/isTab/isEdit/isChecked`，`edit-config.trigger=click` `mode=cell`
- 操作列：hover 显示拖拽手柄（Sortable.js 行排序）+ 行内 加/减
- **商品名称列 = `vxe-pulldown` + 内嵌 `vxe-grid` 下拉**：
  - 双源合并：`/product/customer-sku/list`（客户商品池+配送点覆盖，展示覆盖别名与 priceOverride）+ `/product/temp/list`（临时商品，`isTemp` 标记）
  - 200ms 防抖服务端检索，六维匹配：SKU名 / 助记码 / 客户别名 / 客户编码 / 全局别名 / 配送点覆盖别名
  - 选中正式 SKU → 走 `/price/query` 三层取价；临时商品 → 直接用默认单价
- 表尾 `footer-method` 合计；`edit-rules` 行内校验
- 右侧最近订单 vxe-grid，`current-change` 点击回填参考

❗缺口：**无草稿自动保存**（无 localStorage/Pinia draft 逻辑），刷新丢数据；无 `F2/Ctrl+Enter` 等页面级快捷键；无常用商品面板。

### 4.2 送货单据（2036）— el-table

- 搜索：送货单编号 / 状态 / 配送日期
- 列：编号 / 客户 / 配送点 / 配送日期 / 状态 / **打印次数** / 备注 / 操作
- 操作列：明细（弹窗只读 el-table）/ 打印（status=2 时禁用，`print_count+1`）/ 送达（status=2 时禁用，订单→DELIVERED）/ 删除
- 状态（`t_delivery_order_status`）：`0待打印 → 1已打印 → 2已送达`
- 送货单按「客户 + 配送日期」自动聚合生成

### 4.3 验收单（2081）— el-table

- 操作列按 status=0(草稿) 显隐：录入 / 提交 / 删除；恒显示明细
- 新增对话框：选送货单（下拉展示 `编号（客户/配送点/日期）`）
- **录入弹窗为核心交互**：表格内可编辑「实收数量」，自动计算「损耗 = 送货数量 - 实收」和「实收金额」；**负损耗时损耗原因必填**
- 状态（`t_acceptance_status`）：`0草稿 → 1已提交`

---

## 5. 打印管理（menu 6）

### 打印模板（2042）— el-table

- 搜索：模板名称 / 绑定类型
- 三级绑定优先级：`1 客户+配送点组合 > 2 客户 > 3 全局默认`
- 对话框**字段联动**：bindType=1 显示客户ID+配送点ID；=2 只显示客户ID；=3 显示"设为全局默认"开关
- 引擎：JimuReport，`content` 存模板ID；`copies` 联数
- 操作：修改 / 预览 / 删除

---

## 6. 采购管理（2075）— el-table

- 搜索：采购单号 / 采购日期范围 / 状态
- 工具栏：新增 / **生成采购单**（复用 `purchase:add` 权限）/ 删除
- 操作列按状态显隐：
  - status=0(草稿) → 修改 / 确认 / 删除
  - status=1(已确认) → 入库
- 状态（`t_purchase_order_status`）：`0草稿 → 1已确认 → 2已入库`
- 明细行内编辑：选 SKU 后自动回填名称/规格/单位；数量×单价自动算小计；可增删行

---

## 7. 报表中心（2087）— 两 tab

| tab | 筛选 | 主表列 | 展开行 |
|-----|------|--------|--------|
| 销售日报 | 配送日期 | 客户/配送点/实收金额/损耗数量/损耗金额 | 商品明细（名称/规格/单位/实收数量/损耗/单价/实收金额） |
| 客户对账单 | 客户 + 日期范围 | 验收单号/验收日期/送货单号/验收单金额 | 商品明细 |

两 tab 均支持导出（`report:export`）。使用 el-table `type="expand"` 主从展开。

---

## 8. 全局层交互

| 能力 | 现状 |
|------|------|
| 顶栏 | Navbar：Hamburger 折叠 / Breadcrumb / HeaderSearch（菜单搜索）/ HeaderNotice / Screenfull / SizeSelect / 用户下拉 |
| 侧边菜单 | 可折叠，`Sidebar` |
| 标签页 | RuoYi 原生 tagsView（右键关闭系列），**未实现拖拽排序/固定** |
| 锁屏 | `lock.vue` — 粒子背景 + 时钟 + 密码解锁 |
| 字典 | `dicts: [...]` 选项式声明，`<dict-tag>` 渲染 |
| 权限 | `v-hasPermi` 指令 + 路由 `permissions` + `auth.hasPermiOr` |
| Excel 导入 | `components/ExcelImportDialog`，SPU/SKU/客户/报价页复用 |

---

## 9. 已识别缺口与问题

### 待实现（设计文档已规划）

1. **全局搜索 Ctrl+K** — QuickTable 已 emit `global-search`，**无任何消费方**。需做浮层组件：跨商品/客户/订单/配送点，结果分组，键盘上下选择回车跳转。
2. **录单草稿自动保存** — `sale/detail.vue` 完全无草稿逻辑，刷新即丢。建议 Pinia store + 30s 定时写 localStorage。
3. **常用商品面板** — 按客户历史高频 20 个商品，点击/拖拽入表。需后端新接口。
4. **标签页拖拽/固定** — 当前仅 RuoYi 原生右键菜单。
5. **销售订单页生成采购单/送货单** — `handleBuildPurchase` 只做校验未落地，`handleBuildDelivery` 空函数。

### 一致性问题

6. **两套范式并存** — 10 页仍为 el-table，无快捷键/右键/列设置。建议单据类页面（销售订单/送货单/验收单/采购单）优先接入 QuickTable。
7. **`partner:customerDept:*` 四个权限（add/edit/remove/export）在页面 `v-hasPermi` 中使用，但 `sys_menu` 未定义** → 非 admin 角色永远看不到配送点页的按钮。
8. **`router/index.js` 报价详情路由 `permissions: ['partner:quote:add']` 拼写有误**，实际权限为 `product:quote:add` → 非 admin 用户该路由不会注册。
9. **字典 `t_customer_type`（dict_id=101）只有 dict_type 无 dict_data** → 客户列表"客户类型"列渲染为空。
10. **`price:point:*` 旧权限仍残留在 sql** 中（已被 `price:delivery-override:*` 取代），建议清理。
11. **配送点覆盖别名为内存级覆盖**，管理页 pool 查询命中不到（录单页传 deliveryPointId 时可命中）。

### 已知技术坑（改动时避开）

- vxe v4 `menu-config` 结构为 `{ body: { options: [[...]] } }`，非 v3 直数组
- `vxe-icon-wipe` 不存在，用 `vxe-icon-custom-column`
- 树形表格不支持 `checkbox-config.range`，QuickTable 已自动降级
- 页面 `handleDelete(row)` 需容忍 undefined：`(row && row.id) || this.ids`
- 列设置持久化需 grid `id` + 每列 `field`

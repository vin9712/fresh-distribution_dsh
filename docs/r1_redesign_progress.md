# 基础信息模块重构（deepseek_redesign.md）— 执行进度与无缝衔接手册

> 更新日期：2026-08-22
> 设计文档：`docs/deepseek_redesign.md`（最终目标）
> 现状文档：`docs/基础信息模块梳理.md`（改造前基线）
> UI 设计文档：`docs/deepseek_ui_redesign.md`（前端交互设计指南）
> 下一轮执行人：**从此文档"下一步"章节直接开始**，无需重新调研。

---

## ⚠️ 2026-08-22 追加（新工作流已启动，本手册进入维护态）

交互优化方案（`docs/生鲜配送 ERP 交互优化方案.md`）已立项，按 **`docs/交互优化开发计划.md`** 分阶段执行（Phase 0 商品初始化、Phase 1 录单体验均已 ✅），
本手册保留作为 R1 重构期的执行记录与坑位参考。与本次工作相关的变化：

1. **遗留问题 #1 已解决**：t_product_sku 已通过 `docs/报价demo.csv` 初始化（88 标准 SKU + 86 SPU + 丽宫已发布报价单 BJ2026082200001 + 客户商品池 88 条），见计划文档 Phase 0。
2. **取价引擎 Bug 修复（commit be4f982）**：`selectActivePriceByCustomerAndSku` 条件 `sq.valid = 0` → `1`（3e1db22 引入）。
   原条件与 `selectCustomerActiveQuote`(valid=1) 矛盾，**客户报价层自上线起从未真正命中取价**，单测因 mock mapper 未暴露；
   修复后 `/price/query` 命中丽宫报价（大白菜 1.20 等，source=2），verify-product-init.js 已加回归守护断言。

---

## 0. 当前状态速览

| 阶段 | 内容 | 状态 |
|------|------|------|
| 阶段 0 | 基线确认（编译 + 30 测试全绿） | ✅ 完成 |
| 阶段 1 | 数据层（DDL + 实体 + Mapper） | ✅ 完成 |
| 阶段 2 | 核心服务 + 单测（43 测试全绿） | ✅ 完成 |
| 阶段 3 | 接口层 + curl 全链路验证 | ✅ 完成 |
| 阶段 4 | 前端（SKU 去客户化 / 客户商品页 / 覆盖页 / 临时商品 / 录单） | ✅ 完成 |
| 阶段 5 | 搜索增强 + 全量回归 + 文档收尾 | ✅ 完成 |
| 阶段 6 | UI 改造（deepseek_ui_redesign.md）：QuickTable 组件 + 基础信息 10 页全部接入 | ✅ 完成 |

**前后端当前可运行**（后端 8090，前端 1025），全链路已验证：建标准 SKU → 建模板 → 批量赋值 → 客户商品池 → 配送点覆盖 → 取价。

---

## 1. 环境备忘（下次开跑前确认）

| 项 | 值 |
|----|----|
| JDK | 17（zulu-17.0.13） |
| Maven | 3.9.12，本地仓库 `D:\Dev\Maven\maven-repository`（非默认 ~/.m2！） |
| MySQL | 5.7.28，`localhost:3306`，库 `fresh-distribution-dsh`，root / ljw123 |
| 后端启动 | `mvn -pl lin-entry spring-boot:run`（profile 默认 local，端口 8090） |
| 登录 | admin / admin123，验证码已关闭（sys_config `sys.account.captchaEnabled=false`） |
| 关键命令 | `mvn -pl lin-distribution test -o`（离线跑单测）；改 lin-common 后需 `mvn -pl lin-common,lin-distribution clean install -o -DskipTests` 再重启 |

**⚠️ 启动后端必做**：lin-distribution 改动后必须先
`mvn -pl lin-distribution clean install -o -DskipTests`（**必须 clean**，否则旧 XML 残留在 jar 里导致启动失败），再 `mvn -pl lin-entry spring-boot:run`。
杀端口：`netstat -ano | grep ":8090.*LISTENING"` 拿 PID → `taskkill //F //PID <pid>`。

---

## 2. 已完成工作清单（阶段 0-3）

### 2.1 数据库（sql/r1_basicinfo_redesign.sql，幂等可重跑）

- 重建 `t_product_sku`：**移除 customer_id**；新增 `spec_name`/`is_weighted`/`base_unit`/`conversion_rate`；
  `code` 全局唯一（S+8位）；唯一键 `(category_id, name, spec_name, unit)`；utf8mb4
- 新增 `customers_sku`（客户商品池）：`(customer_id, sku_id)` 唯一、`customer_code` 唯一（C{客户ID}+6位）
- 新增 `customer_group`（客户分组）
- 新增 `delivery_sku_override`（配送点覆盖：is_available / price_override / alias_override / 生效期），`(delivery_point_id, sku_id)` 唯一
- 新增 `default_sku_template` + `default_sku_template_item`（批量赋值模板，不含价格）
- `t_customer` 加 `group_id`；`temp_product` 加 `customer_id` + `converted_sku_id`

### 2.2 后端 Java（lin-distribution）

**实体（domain）**：`ProductSku`（去 customerId，spec→specName，加新字段）｜新建 `CustomerSku`/`CustomerGroup`/`DeliverySkuOverride`/`DefaultSkuTemplate`/`DefaultSkuTemplateItem`｜`Customer`+groupId｜`TempProduct`+customerId/convertedSkuId

**Mapper + XML**：`ProductSkuMapper`（去客户维度、新查重 `selectProductSkuByCategoryNameSpecUnit`）｜新建 5 组 Mapper/XML ｜`TempProductMapper`（新字段支持、未转正过滤、客户范围查询）

**服务（service/impl）**：
- `BizCodeService`：新增 `nextSkuCode()`（sku_code → S+8位）、`nextCustomerSkuCode(customerId)`
- `ProductServiceImpl`：SPU 不再自动建默认 SKU；SKU 插入走全局编码；查重按分类+名称+规格+单位
- 新建 `CustomerSkuService`：CRUD、个性化、`listCustomerProducts()`（§5.2 合并配送点覆盖：隐藏/别名/价格覆盖）
- 新建 `DefaultSkuTemplateService`：模板 CRUD + `batchAssign()` 三策略（1=仅新增 / 2=覆盖未个性化 / 3=全部覆盖）
- 新建 `DeliverySkuOverrideService`：同配送点+SKU upsert
- `PriceQueryServiceImpl`：第 1 层切到 `delivery_sku_override.price_override`（优先级不变：覆盖 > 客户报价 > 模板）
- `TempProductServiceImpl.convertToSku`：事务内查重→建/复用标准SKU→建 customers_sku→回写 converted_sku_id
- `ProductSkuQuoteDetailServiceImpl.customerQuoteDetailList`：改从客户商品池组装报价草稿
- `PinYinConvertUtils.toFirstChar`：修复非汉字字符数组越界（防御性）

**控制器（controller）**：新建 `CustomerSkuController`（/product/customer-sku/**）、`DeliverySkuOverrideController`（/price/delivery-override/**，**替代已删除的 /price/point**）、`DefaultSkuTemplateController`（/product/default-sku-template/**）
**删除**：DeliveryPointPriceController / Service / Mapper / domain / XML（全部旧配送点报价代码）

**单测（31 → 43 全绿）**：新增 `CustomerSkuServiceImplTest`(5)、`DefaultSkuTemplateServiceImplTest`(5)、`DeliverySkuOverrideServiceImplTest`(2)，改造 `PriceQueryServiceImplTest`(6，覆盖表切换+空价回退用例)

### 2.3 已验证的全链路（curl，见 .r1_flow.sh）

```
建SKU(西红柿/斤→S00000001) → 建模板(食堂常用商品) → 批量赋值(客户10→C10000001)
→ 客户商品池查询(含助记码/分类名冗余) → 配送点覆盖(9.9元+别名"洋柿子")
→ 客户商品列表合并覆盖(别名被覆盖、priceOverride=9.90) → 取价(9.90, source=1)  ✅ 全部通过
```

### 2.4 验证中修复的坑（下次遇到直接绕开）

1. **curl 发中文乱码**：Windows 终端 GBK，中文必须写成 UTF-8 JSON 文件 `--data-binary @file` 发送
2. **Jackson 数字类型**：JSON 数字反序列化为 Integer，controller 解析 `List<Long>` 需 `toLongList()` 转换（两个新 controller 已内置）
3. **增量编译残留**：删除 Mapper XML 后必须 `clean install`，否则旧 XML 在 jar 里导致 SqlSessionFactory 解析失败
4. **后端跑旧代码**：`spring-boot:run` 用的是 m2 仓库 jar，lin-distribution 改动后必须 install 再重启

---

## 2.5 阶段 4 已完成工作清单（前端，RuoYi-Vue3）

### T13 标准 SKU 页去客户化 — `src/views/product/sku/index.vue`
- 移除"当前客户"选择器（查询与表单均去客户维度），列表展示全部标准 SKU
- 表单字段对齐新实体：`name`/`specName`/`unit`/`isWeighted`(开关)/`baseUnit`/`conversionRate`/`salePrice`/`saleable`/`valid`
- 删除报价明细列（客户报价已不适用于客户无关 SKU），售价列改为"参考售价"提示
- 保留：分类筛选、SPU 匹配/取消匹配、导入导出；编码由后端自动生成 S+8位
- `src/api/product/sku.js` 无需改动（接口路径未变）

### T14 客户商品页 — `src/views/product/customerSku/index.vue` + `src/api/product/customerSku.js`
- 按客户查询商品池 `GET /product/customer-sku/pool?customerId=&keyword=&status=`
- 表格列：客户商品编码/别名/标准SKU名/标准编码/规格/单位/起订量/步长/状态/是否跟随默认
- 操作：新增（选客户+SKU，已分配 SKU 客户端过滤）、个性化修改（PUT 自动置 is_follow_default=0）、停用/启用、删除
- 批量赋值弹窗：按模板（选 default-sku-template）或手动勾选 SKU → 多选客户 → 策略 1/2/3 → `POST /product/customer-sku/assign`
- 新增模板管理页 `src/views/product/defaultSkuTemplate/index.vue`（模板 CRUD + SKU 明细回显）

### T15 配送点覆盖页 — `src/views/price/pointPrice/index.vue` + `src/api/price/pointPrice.js`
- API 全部改为 `/price/delivery-override/**`（旧 `/price/point/**` 已删除）
- 列：配送点/商品/是否可见(isAvailable)/价格覆盖/别名覆盖/有效期
- 新增即 upsert（同配送点+SKU 存在则更新，`POST /price/delivery-override`）

### T16 临时商品页 — `src/views/product/aliasMapping/index.vue` 临时商品 tab
- 转正弹窗：归属客户 + 商品分类级联 + 助记码（可空）→ `POST /product/temp/{id}/convert`（后端要求 customerId/categoryId 必填）
- 列表展示"已转正/未转正"状态（convertedSkuId 非空为已转正，转正按钮禁用）
- 新增支持"所属客户"（不选=全局临时商品）；列表展示"全局/客户名"标签
- **后端配套**：`TempProduct` 增加 `showAll` 非表字段，`TempProductMapper.xml` 由 `convertedSkuId == null` 改为 `showAll == null or showAll == false` 才过滤未转正；管理页传 `showAll=true` 查看全部，录单仍默认只看未转正

### T17 录单侧商品源切换 — `src/views/order/sale/detail.vue`
- 商品下拉改为双源合并：`GET /product/customer-sku/list?customerId=&deliveryPointId=&keyword=`（客户商品池+配送点覆盖，覆盖别名展示、priceOverride 展示价）+ `GET /product/temp/list?customerId=`（全局+客户专用未转正临时商品，isTemp 标记）
- 选中正式 SKU 仍走 `/price/query` 三层取价；临时商品直接用默认单价不取价
- 搜索框防抖 200ms 服务端关键字检索（匹配 SKU 名/助记码/客户别名/客户编码/全局别名）
- **后端配套**：`DefaultSkuTemplateController` 新增 `GET /product/default-sku-template/{id}/items`（模板明细 SKU 集合，供模板页编辑回显）

### 菜单/权限 SQL — `sql/r1_frontend_menu.sql`（已执行）
- 菜单 2064/2068-2070：配送点报价 → 配送点覆盖，`price:point:*` → `price:delivery-override:*`
- 新增菜单 2090-2094 客户商品（含 assign 权限）、2095-2099 默认SKU模板（挂"基础信息"下）
- 补齐空字典数据：`biz_yes_no`（是/否）、`t_sku_unit`（斤/公斤/箱/袋/份/个/包/瓶/件/捆）

### 阶段 4 验证结果
- `npm run build:prod` 0 报错；vite dev（1025）各新页面模块编译 200
- 后端 43 单测全绿（新增接口无回归）；curl 验证：模板明细 items ✅、临时商品 showAll 过滤 ✅、客户专用临时商品创建+转正+自动入池 ✅、录单双源列表合并 ✅
- 已知限制：配送点覆盖的别名（如"洋柿子"）为内存级覆盖，DB 关键字检索匹配不到（搜索仍命中标准 SKU 名/助记码/客户别名/全局别名），列入 T18 搜索增强可选优化

---

## 6. 阶段 6：前端 UI 改造（deepseek_ui_redesign.md，✅ 已完成 2026-08-21）

### 目标
基于 `docs/deepseek_ui_redesign.md` 对**基础信息**模块全面改造：高密度布局、vxe-table 深度配置、键盘快捷键、右键菜单、批量操作条、列设置持久化。**改造范围仅限 `RuoYi-Vue3`（Vue3 + Element Plus + vxe-table@4 + vxe-pc-ui@4）**，`ruoyi-ui`（Vue2 旧前端）不动。

### 新增共享组件
| 文件 | 说明 |
|------|------|
| `RuoYi-Vue3/src/components/QuickTable/index.vue` | 快捷列表核心组件（vxe-grid 封装）：紧凑搜索区（可折叠）+ 工具栏（自定义列/全屏/刷新/快捷键提示）+ 虚拟滚动 + 行高亮 + 右键菜单 + 批量操作条 + 分页 + 键盘快捷键 |
| `RuoYi-Vue3/src/components/QuickTable/quickTableMixin.js` | 页面通用 mixin：选中状态（ids/single/multiple）、分页事件、批量动作分发（默认 delete 走 handleDelete） |
| `RuoYi-Vue3/src/assets/styles/quick-table.scss` | 紧凑化全局样式（表头/行高/单元格密度/分页） |

### QuickTable 能力（对应设计文档 §3/§4/§5）
- **键盘快捷键**：`F2` 新增 / `F3` 聚焦搜索 / `Delete` 删除选中 / `Ctrl+S` 保存（事件）/ `Esc` 清除选择；快捷键提示弹层（工具栏问号图标）
- **右键菜单**：行右键=新增/修改/删除/刷新；表头右键=刷新/重置列设置；`menu-config` v4 结构为 `body: { options: [[...]] }`（v3 直数组结构在 v4 无效）
- **批量操作条**：选中行后浮出「已选 N 项 + 动作按钮 + 清除选择」，动作由 `batch-actions` prop 配置
- **列设置**：`custom-config.storage=true` + grid `id`，localStorage 键 `VXE_CUSTOM_STORE`，支持显隐/拖拽排序/固定列/宽度记忆
- **表格**：checkbox（多选 range）+ seq + 虚拟滚动（gt 100）+ resizable + 双击行=编辑 + 行 hover 高亮
- **搜索区**：右上折叠箭头（v-model:showSearch 双向）

### 接入页面（10 个，均在基础信息菜单下）
| 页面 | 文件 | 说明 |
|------|------|------|
| 客户管理 | `views/partner/customer/index.vue` | 参考实现；右键菜单新增「复制」扩展点 |
| 客户部门(配送点) | `views/partner/customer/dept.vue` | 保留 deptCodes 删除提示 |
| 商品分类 | `views/product/category/index.vue` | 树形表格（tree-config），展开/折叠按钮走 `setAllTreeExpand` |
| 商品SPU | `views/product/spu/index.vue` | |
| 标准SKU | `views/product/sku/index.vue` | 批量操作条含「批量关联」；`titleHelp` 展示参考售价提示 |
| 客户商品 | `views/product/customerSku/index.vue` | 无分页；批量条含「停用/启用」 |
| 默认SKU模板 | `views/product/defaultSkuTemplate/index.vue` | 无分页 |
| 配送点覆盖 | `views/price/pointPrice/index.vue` | 无分页 |
| 报价 | `views/product/quote/index.vue` | 保留状态操作列（发布/撤销/失效） |
| 别名/映射/临时商品 | `views/product/aliasMapping/index.vue` | 3 个 tab 各自 QuickTable，`shortcuts=false` 防冲突 |

### 已知坑（下次遇到直接绕开）
1. **vxe v4 menu-config 结构**：`{ body: { options: [[{code,name,prefixIcon}...]] } }`，不是 v3 的直接数组
2. **vxe v4 图标类名**：`vxe-icon-*` 在 vxe-pc-ui 样式里（249 个），`vxe-icon-wipe` 不存在，用 `vxe-icon-custom-column`；element 图标字符串需全局注册（本项目已 `app.use(elementIcons)`）
3. **树形表格**不支持 `checkbox-config.range`（控制台警告），QuickTable 已按 treeConfig 自动降级
4. **右键菜单点击行**：事件目标必须在 `td.vxe-body--column` 内；对 `tr` 合成 contextmenu 无效（测试时注意）
5. **页面 handleDelete(row)** 需容忍 undefined（批量条/Delete 键走无参调用），统一写成 `(row && row.id) || this.ids`
6. **列设置持久化**：customConfig.storage 需要 grid `id` + 每列 `field`（QuickTable 已自动补操作列 field）
7. **批量条无动作按钮时不显示**（batch-actions 为空数组 → 只显示计数+清除）

### 验证结果
- `npm run build:prod` 0 报错
- puppeteer 冒烟（`.dsh-e2e/verify-quicktable.js`，gitignore）：8 页全部渲染 vxe-grid、0 控制台错误
- 交互验证：F2 新增对话框 ✅ / F3 聚焦搜索 ✅ / 双击行编辑 ✅ / 右键菜单→修改 ✅ / 勾选批量条→删除确认 ✅ / 列设置显隐+localStorage 持久化+刷新恢复 ✅

### 下一步（可选）
- 全局快捷键 `Ctrl+K` 全局搜索（设计文档 §5.1）：QuickTable 已预留 `global-search` 事件
- 录单页/订单页接入 QuickTable 或深度配置（订单页已有 vxe 表格，需人工评估）
- 常用商品面板、草稿自动保存（keep-alive 已有）+ Pinia 草稿状态（设计文档 §6）

---

## 3. 下一步：阶段 5 收尾（✅ 已完成 2026-08-21）

- [x] **T18 搜索增强确认**：六维检索全部验证命中——标准SKU名(草莓)/助记码(XHS)/客户别名(柿子)/客户编码(C10000002)/全局别名(番茄)/**配送点覆盖别名(洋柿子，本次新增)**；数据量小无需缓存
- [x] **T19 全量回归**：
  - `mvn -pl lin-distribution test -o`：44 用例全绿（43+新增 search 透传用例）
  - E2E 全链路（`.dsh-e2e/e2e-full-flow.js`）：录单→确认→采购→送货→打印→送达→验收→销售日报 ✅ 不受 SKU 表结构变化影响
  - 历史订单：t_sale_order_detail 旧 sku_id(10/11) 无对应标准 SKU，但 product_name 快照正常，订单详情/明细接口不崩
- [x] 更新 `docs/基础信息模块梳理.md`（顶部加重构后现状速览对照表）与 `docs/deepseek_redesign.md`（标注已落地项）
- [x] git commit（见下方 §7）

### T18 配套改动
- `CustomerSkuMapper.xml`：`selectCustomerSkuList` 支持 `deliveryPointId` 条件 join `delivery_sku_override`，关键字可命中覆盖别名（录单传配送点才有该语义，管理页 pool 不受影响）
- `CustomerSkuServiceImpl.listCustomerProducts`：把 deliveryPointId 透传给 mapper 查询
- 新增单测：关键字搜索时配送点透传验证

---

## 7. 阶段 5 验证结果
- 前端 `npm run build:prod` 0 报错；后端 44 单测全绿
- E2E 全链路通过（订单 XD202608210001 / 采购 PC20260821001 / 送货 HS20260821001 / 验收 YS20260821001）
- git 提交：按 数据层+服务层 / 接口层+前端 / 文档 分 3 个 commit（见 git log）

---

## 4. 遗留问题与后续扩展（非阻塞）

1. **历史数据** ✅ 已解决（2026-08-22）：t_product_sku 已通过 `docs/报价demo.csv` 初始化（88 标准 SKU + 86 SPU + 客户商品池），
   并生成丽宫已发布报价单 BJ2026082200001；详见 `docs/交互优化开发计划.md` Phase 0（幂等脚本 `sql/s7_product_init.sql`）
2. ~~菜单/权限 SQL 未写~~ ✅ 已落地（`sql/r1_frontend_menu.sql`，阶段 4）
3. 报价单/模板的 skuId 语义已指向标准 SKU，无需改表，代码已验证
4. 库存/采购/单位换算为设计文档 §9 预留项，未实现（符合"不过度设计"原则）
5. `deleteProductSpuByIds` 的关联 SKU 校验仍是 TODO（原系统遗留）
6. 配送点覆盖别名（内存级）暂不可被关键字检索命中，如需支持列入 T18 搜索增强

---

## 5. 关键文件索引

| 用途 | 路径 |
|------|------|
| 设计文档 | `docs/deepseek_redesign.md` |
| DDL 脚本（幂等） | `sql/r1_basicinfo_redesign.sql` |
| 前端菜单/权限/字典 SQL | `sql/r1_frontend_menu.sql` |
| 全链路验证脚本 | `.r1_flow.sh`（需先登录拿 token 写入 `.r1_token.txt`） |
| 旧基线文档 | `docs/基础信息模块梳理.md` |
| 测试 | `lin-distribution/src/test/java/com/lin/distribution/service/impl/*Test.java` |
| 前端新页面 | `RuoYi-Vue3/src/views/product/customerSku/`、`RuoYi-Vue3/src/views/product/defaultSkuTemplate/`、`RuoYi-Vue3/src/views/price/pointPrice/`、`RuoYi-Vue3/src/views/product/sku/`、`RuoYi-Vue3/src/views/product/aliasMapping/`、`RuoYi-Vue3/src/views/order/sale/detail.vue` |

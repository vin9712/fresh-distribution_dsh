# 开发环境说明（DSH Agent 专用）

本文件记录在 DSH（DeepSeek Harness）环境下操作本项目的已验证规则。**每个新会话的 agent 应先读此文件再执行 Node/npm/git 命令。**

## 0. 当前会话策略（以此为准）

- 文件策略：**danger-full-access**（文件读写不受限制）
- 审批策略：**never**（不可申请提权；不要传 `sandbox_permissions` 参数）
- 实测：named pipe 放行（`child_process.exec/spawn` 捕获输出可用）、PowerShell 全语言模式
- 结论：**npm / git / node 均按常规方式直接使用**，无需任何绕过。

```powershell
npm install            # 可直接用 npm（全局缓存 D:\Dev\NodeJs\node_cache 可写）
npm run dev
git log | ForEach-Object { $_ }   # 管道捕获也正常
```

## 1. 历史沙箱边界（仅当未来策略回退到 workspace-write 时参考）

| 操作 | 旧限制 |
| --- | --- |
| Node `child_process.exec/spawn`（pipe 捕获子进程输出） | 曾报 `spawn EPERM`（named pipe 被禁） |
| PowerShell 7 管道捕获原生程序输出 | 曾被拒 |
| 写工作区外路径（如 npm 全局缓存） | 曾报 EPERM |
| `npm`（ps1 shim） | 曾在受限语言模式下报 `$LASTEXITCODE` 未定义 |

当时的工作区方案（如策略回退可重新启用）：
1. 用 `npm.cmd` 代替 `npm`；
2. 设置 `$env:npm_config_cache = "D:\myProject\myGit\fresh-distribution_dsh\.npm-cache"`（已加入 .gitignore）；
3. 安装脚本加 `--foreground-scripts`（esbuild 等 postinstall 仍需提权）；
4. 避免用管道捕获原生程序输出。

## 2. Node 版本

- `D:\Dev\NodeJs\v24.14.1`（当前 PATH，npm 11.11.0）、`D:\Dev\NodeJs\v18.20.5`（npm 10.8.2）并存。
- 切换版本（仅当前命令生效）：`$env:PATH = "D:\Dev\NodeJs\v18.20.5;" + $env:PATH`
- ruoyi-ui（vue2 + vue-cli 4.4.6/webpack 4）：node 24 实测可编译。
- RuoYi-Vue3（vite 6）：node 24 可用。

⚠️ **PowerShell 是 5.1**：`Get-Content`/`Set-Content` 默认用 ANSI(GBK) 编码，读写无 BOM 的 UTF-8 文件会**损坏中文并吞掉引号**（曾导致 8 个 .vue 文件损坏重迁）。用 pwsh 改文本文件时**必须**显式 `-Encoding UTF8`（读和写都要）；改写源码优先用 read/edit/write 工具。

## 3. 项目启动

- 后端（Java 17, Spring Boot）：profile=local，端口 **8090**，入口模块 `lin-entry`。
- ruoyi-ui（vue2 旧前端，迁移源）：
  ```powershell
  cd ruoyi-ui
  npm.cmd run dev   # http://localhost:1024 ，代理后端 localhost:8090
  ```
- RuoYi-Vue3（vue3 目标工程，迁移目的地）：
  ```powershell
  cd RuoYi-Vue3
  npm run dev       # http://localhost:1025（已改），代理后端 localhost:8090（已改）
  ```

## 4. 迁移记录

- vue2 业务菜单由后端 DB 动态加载（sql/new_added_sql.sql 等），组件路径如 `product/spu/index`；RuoYi-Vue3 以 `import.meta.glob('views/**/*.vue')` 解析，**业务文件须保持同名路径**。
- vue3 已补依赖：`vxe-table@^4`、`vxe-pc-ui@^4`、`xe-utils`、`pinyin-pro@^3.26.0`；main.js 已注册 `VxeUITable` + `VxeUIPc`。
- 打印模块（vue-plugin-hiprint / vue-ls / PrintModule / demo）暂缓迁移，vue3 用占位页。
- vue2 死代码（不迁）：vue-easytable、FilterHeader、index_v1.vue、order/demo、delivery/detail.vue。
- ruoyi-ui 已修复缺失依赖：`@vxe-ui/core@^3.4.20`、`xe-utils@^3.9.1`、`bwipjs@npm:bwip-js@^4`（记录于 package.json）。

## 5. 运行时问题修复记录（无头浏览器实测）

排查工具：`.dsh-e2e/`（puppeteer-core@8 + 本机 Edge 89 无头；真实登录 admin/admin123、验证码固定输入 1；逐菜单点击 + 弹窗/详情页交互，抓 pageerror/console）。

1. **业务页白屏（Cannot read properties of null (reading 'parentNode')）**：根因是迁移页模板沿用 Vue2 的 `dict.type.xxx`，而 RuoYi-Vue3 3.9.2 没有该全局 mixin（只有 script-setup 的 `useDict`），`dict` 未定义导致渲染崩溃并连锁破坏路由过渡。修复：新增 `src/plugins/dict.js`（全局 mixin，读取组件 `dicts: [...]` 选项，向 pinia dict store 拉取并映射为 `{label,value}`），main.js 注册。
2. **每页报 `/system/notice/listTop` 参数类型不匹配**：v3.9.2 导航栏 HeaderNotice 公告铃铛调用后端不存在的 listTop/markRead 接口（后端无此表/接口）。修复：Navbar.vue 摘除 `<header-notice>`（与 Vue2 对齐），HeaderNotice 组件保留未用。
3. **销售订单详情 Sortable 报 `el must be an HTMLElement, not [object Null]`**：vxe-table v4 的 `.vxe-table--body` 不再直接位于 `.body--wrapper` 下（中间多一层 `.vxe-table--body-inner-wrapper`），`>` 子选择器失效。修复：改为后代选择器并加空值保护。
4. **`structuredClone is not defined`（旧浏览器）**：内联的 `deepCloneWithoutFields` 兜底为 JSON 深拷贝（Chrome 98+ 才有 structuredClone）。
5. 注意：element-plus 弹窗/overlay 关闭后仍留在 DOM（21 个历史 overlay），E2E 断言必须按可见性（offsetParent/display）而非首个元素判断。

## 6. 兼容性告警修复（element-plus 弃用 API，无头浏览器实测确认清零）

- `el-button type="text"` → `link`（业务页 21 处；注意 vxe-input 的 `type="text"` 合法不可改）。
- `el-radio :label`（label 作 value，element-plus 3.0 将移除）→ `:value`（spu/sku/customer/dept 共 6 处）。
- `[ElOnlyChild] no valid child node found`：product/quote/index.vue 遗留的空 `<el-popover>`（vue2 死代码，element-ui 不校验、element-plus 会告警）→ 删除。
- vxe-table v4 `resizable` 表属性已弃用 → sale/detail 改为 `:column-config="{ resizable: true }"`。
- element-plus 的 debugWarn 走 console.warn 且参数是 Error 对象；Puppeteer 抓取时需 `msg.args()[i].executionContext().evaluate(o => o instanceof Error ? o.message : String(o))` 才能拿到文本。

## 7. 重设计实施记录（切片进度）

- S0-1 单号服务：`biz_code_seq` 表 + `BizCodeService`（UPDATE seq=LAST_INSERT_ID(seq+1) 自增、INSERT IGNORE 竞争重试）；订单 XD+日4位、送货 HS+日3位、报价 BJ+日5位（每日重置）；状态枚举语义升级码值不变。**教训**：INSERT...ON DUPLICATE KEY UPDATE 的首插分支 LAST_INSERT_ID() 会残留同连接旧值，必须用 UPDATE 先行 + INSERT IGNORE 模式。
- S0-2 表结构：11 张新表 + 3 处复用表 ALTER（sql/s0_2_table_baseline.sql；一次性脚本，重复执行会报 Duplicate column）。
- S0-3 JimuReport：依赖 `org.jeecgframework.jimureport:jimureport-spring-boot3-starter-fastjson2:2.0.0`（Boot3.4/JDK17 系）；schema=`sql/s0_3_jimureport_init.sql`（17 张 jimu_* 表，导入需先 `SET GLOBAL max_allowed_packet=67108864`）；鉴权桥接：SecurityConfig 放行 `/jmreport/**` + `lin-entry/.../jmreport/JimuReportTokenServiceImpl`（实现 `JmReportTokenServiceI`，用 `TokenService.getLoginUserByToken` 校验 token 参数）。**教训**：本项目 createToken 不放 subject，getUsernameFromToken 恒 null，必须走 LOGIN_USER_KEY→Redis 校验。设计器入口 `/jmreport/list?token=<JWT>`。
- 后端验证套路：`mvn -q -T 1C install -DskipTests`；临时实例 `mvn -q -pl lin-entry spring-boot:run "-Dspring-boot.run.main-class=com.lin.FreshDistributionApplication" "-Dspring-boot.run.arguments=--server.port=8091"`（必须先 install 兄弟模块；`-am` 会让插件在 root 聚合器上跑而报无主类）。
- DB 直连（.dsh-e2e 有 mysql2）：localhost:3306 root/ljw123 fresh-distribution-dsh。
- E2E 菜单点击：侧边栏子菜单默认收起，需先 mouse.click 父菜单标题展开，再点可见的 .el-menu-item；图标型入口（如配送点=客户名称上的 a.link-type）按选择器而非文字找。
- S1-1 配送点：客户部门→配送点纯文案/日志更名（表/字段/API 不变），提交 d161c9d。
- S1-2 别名与映射：product_alias/customer_sku_mapping/temp_product 全栈 + 前端 aliasMapping 三 Tab 页 + 临时商品转正式 SKU；菜单 2050-2060 段。**教训**：ProductSku.builder() 不含 BaseEntity 继承字段（createTime 要 build 后 set）。
- S1-3 导入：SPU 导入（模板=导出模板，同分类+名称查重用 selectProductSkuByCategoryIdAndName 模式）；报价导入（ProductSkuQuoteImportDTO 模板列=客户ID/SKUID/单价/生效/失效；按客户聚合、日期取行集合并集；走 createSkuQuote 自带"生效日须晚于当前有效报价失效日"校验）；菜单 2061-2062。前端导入对话框模式照抄 sku/index.vue。
- S2 报价：现状报价已带有效期（S2-1 只补了"已过期"标记）；S2-2 模板/配送点报价（子代理全栈）；S2-3 三层取价 PriceQueryService + 5 个 Mockito 单测（lenient 处理 setUp 冗余 stub）。取价 SQL：配送点/模板价=区间内 effective_date DESC 最新；客户报价=status=1 且 valid=0 区间内。
- S3 订单：S3-1 确认订单不再自动建送货单（原 CONFIRMED 分支移除）、撤回校验已生成送货单（selectListByOrderIdIn）；S3-2 工作台五口径（WorkbenchMapper 单条 SQL 聚合）；S3-3 录单选品后调 /price/query 覆盖价格+空价警示；listSku 检索优先级（映射>别名>拼音>品名，SQL in 子查询排序）。
- S3-4 加退换（子代理）：OrderAdjustment 全栈；仅 DELIVERED/ACCEPTED 可调、SETTLED 拒绝；已有行 num+=delta 不得为负并重算 expect_amount；新增行插明细；order_adjustment 记 detail_json；订单金额重算+adjust_flag='1'。菜单 2074（order:sale:adjust）。主控修正：builder().createTime() 编译错误（BaseEntity 不在 builder）→ build 后 set；创建接口权限 order:sale:edit→order:sale:adjust（与按钮一致）。
- S4 采购（子代理）：purchase_order/purchase_item 表在 S0-2 基线已有；PurchaseOrder 全栈 + 23:00 定时 generateTomorrowPurchaseOrder + 手工单（source_type 1自动/2手工）+ 确认/入库；菜单 2075-2078。主控修正：selectSummaryByDeliveryDate 原来只 group by sku_id（临时商品 sku_id 为 null 会全并成一行）→ 改按 sku_id+品名+规格+单位分组。
- S5-1 送货生成（主控）：t_delivery_order 加 delivery_point_id（s5_1_delivery_alter.sql）+ 明细表去 unq_order_id、加商品快照列；明细按商品合并、不带订单号（order_code 存 ''）；generate 幂等（同日已存在则拒绝）+ 仅 CONFIRMED 订单聚合（SaleOrderDetailMapper.selectAggregatedByDeliveryDate join t_sale_order status=1）；markPrinted print_count++（已送达拒绝）；markDelivered 同组订单→DELIVERED（SaleOrderMapper.updateStatusByDeliveryGroup）；撤回守卫改用 countByCustomerIdAndDeliveryDate（明细不再关联订单）；ScheduledTasks 加 06:00 generateTodayDeliveryOrder（幂等冲突仅 warn）；菜单 2079-2080（print/deliver 按钮）。前端 delivery 页重写（生成/明细/打印/送达，dict=t_delivery_order_status）；8 个 Mockito 单测。**教训**：BigDecimal 断言必须 compareTo（scale 不同 equals 失败）；聚合在 SQL 做，service 按聚合行逐一落明细。
- S5-2 验收（主控）：Acceptance 全栈；createByDeliveryOrder（仅已送达送货单、一单一验 countByDeliveryOrderId+uk 兜底、明细默认实收=送货）；updateDraft（仅草稿；后端按 DB 单价重算 actual_amount、损耗=实收−送货、负损耗必填原因、非负清空原因）；submit（订单 DELIVERED→ACCEPTED 复用 updateStatusByDeliveryGroup）；delete 仅草稿；单号 YS+日3位。菜单 2081-2086 + 字典 t_acceptance_status（dict_type 109）；前端 acceptance 页（选已送达送货单建单/查看/录入/提交）；9 个 Mockito 单测。验收表 acceptance/acceptance_item 在 S0-2 基线已建（uk_delivery_order_id 一单一验兜底）。
- 菜单 ID 已用至 2086，新菜单从 2087 起。

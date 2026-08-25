# 技术设计：报价单导入「批量建品」向导（未匹配行一键转正式商品体系）

> 状态：v1.0（2026-02，经用户确认的三步建品流程细化实现）
> 关联模块：商品报价导入（`/product/quote/import*`）、临时商品转正、客户商品、别名与映射
> 涉及代码：
> - 后端 `lin-distribution`：`ProductSkuQuoteServiceImpl`、`TempProductServiceImpl`、`QuotePriceImportConfirmDTO`
> - 前端 `RuoYi-Vue3/src/views/product/quote/index.vue`

---

## 1. 背景与问题

文员的日常：拿到客户商品报价单 → 「商品报价 → 导入」→ 系统四级匹配
（名称精确 → 助记码 → 全局别名 → 客户映射）→ 未匹配行目前只有两个出路：

1. **转临时商品**：之后在「别名与映射」页逐个手工转正；
2. **跳过**。

### 1.1 现状缺陷（已从代码验证）

| # | 缺陷 | 位置 |
|---|------|------|
| D1 | 转正时把临时商品名（=客户原始叫法，如"PG"、"黄心土豆"）**直接当标准SKU名**，商品库被客户叫法污染 | `TempProductServiceImpl.convertToSku` L159-172 |
| D2 | 转正后**不写 `customers_sku.alias`**（客户叫法丢失）、**不写 `CustomerSkuMapping`**、**不挂 SPU** | 同上 L176-181 |
| D3 | 导入确认只写报价明细+临时商品，**不产生任何映射**；四级匹配中的第3/4级全靠文员手工维护 | `ProductSkuQuoteServiceImpl.confirmQuotePriceImport` L663-770 |
| D4 | 未匹配行无法当场批量转正式商品，必须二次进页面操作 | 前端导入向导 step2 |

### 1.2 目标流程（用户确认）

```
客户报价单行 "黄心土豆 45"
   ├─ 匹配成功 → 直接生成报价（现状保留）
   └─ 未匹配 → 批量建品向导（人工定标准名/分类/单位）→ 一个事务内完成：
        ① 建/复用 SPU（按 分类+标准名 归并；spuId 可空，兼容存量无主 SKU）
        ② 建/复用 SKU（分类+名称+规格+单位 查重）
        ③ 建 customers_sku（alias = 客户原始叫法）
        ④ 写 CustomerSkuMapping（客户叫法 → SKU）
        ⑤ 回写对应临时商品的转正标记（如存在）
        ⑥ 该行直接进入本次报价单
     下次导入同一客户 → 四级匹配在第4级"客户映射"直接命中
```

## 2. 数据模型（无表结构变更）

复用现有表，不新增字段：

| 表 | 用途 | 本次写入点 |
|----|------|-----------|
| `t_product_spu` | 标准商品 | 按 categoryId+name 复用，否则新建（含助记码） |
| `t_product_sku` | 规格/SKU | 按 categoryId+name+specName+unit 复用，否则新建（`spuId` 尽力回填，可空） |
| `customers_sku` | 客户商品池 | upsert（customerId+skuId），写入 `alias`=客户原始叫法 |
| `customer_sku_mapping` | 客户叫法映射 | upsert（customerId+customerAlias），skuId 指向新建/复用 SKU |
| `temp_product` | 临时商品 | 同客户同名未转正的回写 `converted_sku_id` |

> 设计原则：**能复用绝不重建**。查重键与现有 `convertToSku` 保持一致，避免同一商品多入口产生重复数据。

## 3. 后端设计

### 3.1 新增服务 `ProductCreationService`

统一"建品"入口，供报价导入与临时商品转正两处调用：

```java
public interface ProductCreationService {
    /**
     * 建/复用 SPU→SKU→客户商品→客户映射 全链路。
     * @return skuId
     */
    Long createOrReuse(ProductCreationDTO dto);
}
```

`ProductCreationDTO`：`customerId, standardName, alias(客户原始叫法), categoryId, unit, spec(可空), price(可空)`。

实现要点（全部幂等）：

1. **SKU 查重**：`selectProductSkuByCategoryNameSpecUnit(categoryId, standardName, spec, unit)`，命中即复用；
2. **SPU 归并**：`selectProductSpuByCategoryIdAndName(categoryId, standardName)`，命中即复用并回填 `sku.spuId`；未命中则新建 SPU（助记码自动生成）。归并是**尽力而为**——存量无 spuId 的 SKU 不受影响；
3. **新建 SKU**：编码走 `bizCodeService.nextSkuCode()`，售价取 price（可空则 0），saleable=1；
4. **customers_sku upsert**：存在（customerId+skuId）则跳过；不存在则插入，`alias=alias`、单位、起订量1、步长1、`isFollowDefault=0`；
5. **mapping upsert**：同 (customerId, customerAlias) 已指向该 skuId 则跳过；指向其他 SKU 则**跳过不改**（保守，避免覆盖文员已确认的映射），报告中提示；
6. **临时商品回写**：查同 customerId+name 且未转正的临时商品，命中第一条回写 `converted_sku_id`。

### 3.2 DTO 扩展 `QuotePriceImportConfirmDTO.Row`

新增三个可空字段：

```java
/** 是否批量建品（未匹配行专用）：true 时忽略 unmatchedToTemp */
private Boolean createFlag;
/** 建品标准名（清洗后的人工确认名，默认建议=rawName） */
private String standardName;
/** 商品分类ID（建品必填校验在服务端） */
private Long categoryId;
```

### 3.3 `confirmQuotePriceImport` 分支改造

未匹配行的处理优先级变为：

```
createFlag=true  && standardName/categoryId 有效 → ProductCreationService.createOrReuse() → 计入 detailMap（进本张报价单）
否则            → 现状逻辑（unmatchedToTemp ? 转临时 : 跳过）
```

返回报告增加一行：`批量建品 N 条（新建 SKU x、复用 y），已建立客户映射 z 条`。

事务不变：整个 confirm 仍在单个 `@Transactional` 内。

### 3.4 `TempProductServiceImpl.convertToSku` 一致性修复（修 D2 的映射缺口）

在现有转正流程上补写两步（保持原有 SKU 建复用行为不变）：

- **3a** `customer_sku_mapping` upsert：customerAlias=临时商品名 → skuId（同别名已指向其他SKU时跳过不覆盖）；
- **3b** `customers_sku.alias` 补写：池条目缺别名时补为临时商品名。

效果：转正后下次该客户订单/导入不再重复产生同名临时商品。

> 注：D1（叫法污染标准名）的彻底解决依赖批量建品向导里的人工标准名确认；手工转正路径保持原名行为不变，只补齐映射。

## 4. 前端设计（`quote/index.vue` 导入向导）

### 4.1 step2 未匹配行处理模式改为三选一

```
未匹配 N 行的处理方式：  (●) 批量建品（推荐）  ( ) 转临时商品  ( ) 跳过
```

- **批量建品**：未匹配行表格展开编辑列——`标准名`(input，默认=rawName)、`分类`(级联/树选择，懒加载 `listCategory`)；单位沿用解析值；
- 校验：任一未匹配行分类为空时禁用"确认导入"并提示；
- 提交体每行携带 `createFlag / standardName / categoryId`。

### 4.2 结果展示

沿用现有 HTML 报告弹窗，无需改动。

## 5. 权限与兼容性

- 复用现有权限 `product:quote:*`，不新增菜单权限；
- 接口仅扩展现有 `/product/quote/importConfirm` 请求体（向后兼容，旧前端不带新字段行为完全不变）；
- `unmatchedToTemp` 字段保留，语义降级为"非建品行"的兜底开关。

## 6. 测试要点

1. 未匹配行建品：全新商品 → SPU/SKU/customer_sku/mapping 各 1 条，报价单含该行；
2. 重复导入同一报价单 → 第二次四级匹配命中"客户映射"，零临时商品；
3. 同名不同单位（土豆/斤、土豆/箱）→ 2 个 SKU 挂同一 SPU；
4. mapping 冲突（别名已指向其他 SKU）→ 不覆盖，报告提示；
5. 旧请求体（无新字段）→ 行为与改造前一致；
6. 手工转正临时商品 → 自动补 alias + mapping。

## 7. 实施清单

| 步骤 | 文件 | 内容 |
|---|---|---|
| B1 | `dto/ProductCreationDTO.java` 新增 | 建品入参 |
| B2 | `service/ProductCreationService.java` + `impl/ProductCreationServiceImpl.java` 新增 | 幂等建品链路 |
| B3 | `dto/QuotePriceImportConfirmDTO.java` | Row 加 createFlag/standardName/categoryId |
| B4 | `impl/ProductSkuQuoteServiceImpl.java` | 注入新服务，未匹配行分支+报告 |
| B5 | `impl/TempProductServiceImpl.java` | convertToSku 补 alias/mapping/SPU |
| F1 | `views/product/quote/index.vue` | 三态模式切换 + 建品编辑列 + 提交体扩展 |

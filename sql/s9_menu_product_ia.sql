-- ============================================================
-- s9_menu_product_ia.sql — 基础信息菜单信息架构优化（可移植版）
-- ============================================================
-- 用途：消除「商品库 / 商品信息 / 客户商品」命名歧义，
--       按"客户 → 品类 → 商品 → 规格 → 客户适配"的数据流向排列菜单。
--
-- ★ 可移植性说明：
--   本脚本【不依赖 menu_id】，全部通过 parent_id=4（基础信息目录）
--   + component 组件路径 定位行，可直接在其他基于 RuoYi 表结构的
--   项目上执行（前提：基础信息目录 parent_id=4、组件路径一致）。
--
-- 变更内容：
--   1. 「商品信息」（product/sku/index）更名「商品规格」
--   2. 基础信息(parent_id=4)下菜单统一重排 order_num：
--        1 客户信息          （往来单位放最前）
--        2 商品分类
--        3 商品库            （商品本体：叫什么/归属哪个分类）
--        4 商品规格          （可售单元：规格/单位/售价）
--        5 客户商品          （客户可购范围 + 别名/起订量）
--        6 别名与映射        （客户叫法 ↔ 我方SKU 映射）
--        7 默认SKU模板
--        8 报价模板
--        9 配送点覆盖
--       10 商品报价
--
-- 执行前建议备份 sys_menu；执行后需重新登录刷新菜单缓存。
-- ============================================================

-- 1. 更名（按组件路径定位，避免依赖 menu_id）
UPDATE `sys_menu`
SET `menu_name` = '商品规格',
    `remark`    = '商品可售规格/单位（SKU）'
WHERE `parent_id` = 4
  AND `component` = 'product/sku/index'
  AND `menu_type` = 'C';

-- 2. 统一排序（按组件路径定位）
UPDATE `sys_menu` SET `order_num` = 1  WHERE `parent_id` = 4 AND `component` = 'partner/customer/index'      AND `menu_type` = 'C'; -- 客户信息
UPDATE `sys_menu` SET `order_num` = 2  WHERE `parent_id` = 4 AND `component` = 'product/category/index'      AND `menu_type` = 'C'; -- 商品分类
UPDATE `sys_menu` SET `order_num` = 3  WHERE `parent_id` = 4 AND `component` = 'product/spu/index'           AND `menu_type` = 'C'; -- 商品库
UPDATE `sys_menu` SET `order_num` = 4  WHERE `parent_id` = 4 AND `component` = 'product/sku/index'           AND `menu_type` = 'C'; -- 商品规格
UPDATE `sys_menu` SET `order_num` = 5  WHERE `parent_id` = 4 AND `component` = 'product/customerSku/index'   AND `menu_type` = 'C'; -- 客户商品
UPDATE `sys_menu` SET `order_num` = 6  WHERE `parent_id` = 4 AND `component` = 'product/aliasMapping/index'  AND `menu_type` = 'C'; -- 别名与映射
UPDATE `sys_menu` SET `order_num` = 7  WHERE `parent_id` = 4 AND `component` = 'product/defaultSkuTemplate/index' AND `menu_type` = 'C'; -- 默认SKU模板
UPDATE `sys_menu` SET `order_num` = 8  WHERE `parent_id` = 4 AND `component` = 'price/template/index'        AND `menu_type` = 'C'; -- 报价模板
UPDATE `sys_menu` SET `order_num` = 9  WHERE `parent_id` = 4 AND `component` = 'price/pointPrice/index'      AND `menu_type` = 'C'; -- 配送点覆盖
UPDATE `sys_menu` SET `order_num` = 10 WHERE `parent_id` = 4 AND `component` = 'product/quote/index'         AND `menu_type` = 'C'; -- 商品报价

-- 3. 验证（执行后检查目标顺序）
-- SELECT menu_id, menu_name, order_num, component
-- FROM sys_menu WHERE parent_id = 4 AND menu_type = 'C'
-- ORDER BY order_num;

-- ============================================================
-- s9_menu_product_ia.sql — 商品资料菜单信息架构优化
-- 目的：消除「商品库 / 商品信息 / 客户商品」命名歧义，
--       按"品类 → 商品 → 规格 → 客户适配"的数据流向排列菜单。
-- 变更：
--   1. 「商品信息」(2018) 更名「商品规格」（实际维护的是可售规格/单位，即 SKU）
--   2. 基础信息(parent=4)下菜单统一重排 order_num：
--      1 客户信息        （往来单位放最前）
--      2 商品分类
--      3 商品库          （商品本体：叫什么/归属哪个分类）
--      4 商品规格        （可售单元：规格/单位/售价，即原"商品信息"）
--      5 客户商品        （客户可购范围 + 别名/起订量）
--      6 别名与映射      （客户叫法 ↔ 我方SKU 映射）
--      7 默认SKU模板
--      8 报价模板
--      9 配送点覆盖
--     10 商品报价
-- ============================================================

-- 1. 更名
UPDATE `sys_menu` SET `menu_name` = '商品规格', `remark` = '商品可售规格/单位（SKU）' WHERE `menu_id` = 2018;

-- 2. 统一排序
UPDATE `sys_menu` SET `order_num` = 1  WHERE `menu_id` = 2012; -- 客户信息
UPDATE `sys_menu` SET `order_num` = 2  WHERE `menu_id` = 2000; -- 商品分类
UPDATE `sys_menu` SET `order_num` = 3  WHERE `menu_id` = 2006; -- 商品库
UPDATE `sys_menu` SET `order_num` = 4  WHERE `menu_id` = 2018; -- 商品规格
UPDATE `sys_menu` SET `order_num` = 5  WHERE `menu_id` = 2090; -- 客户商品
UPDATE `sys_menu` SET `order_num` = 6  WHERE `menu_id` = 2050; -- 别名与映射
UPDATE `sys_menu` SET `order_num` = 7  WHERE `menu_id` = 2095; -- 默认SKU模板
UPDATE `sys_menu` SET `order_num` = 8  WHERE `menu_id` = 2063; -- 报价模板
UPDATE `sys_menu` SET `order_num` = 9  WHERE `menu_id` = 2064; -- 配送点覆盖
UPDATE `sys_menu` SET `order_num` = 10 WHERE `menu_id` = 2024; -- 商品报价

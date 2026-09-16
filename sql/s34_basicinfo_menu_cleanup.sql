-- =============================================================================
-- s34：基础信息菜单清理 —— 移除已下线残留「报价模板」
--
-- 背景：W0-1（w01_price_simplify.sql，2026-08-26）已取消价格层级中的「报价模板」：
--   表 price_template* 已删、取价链路已去除模板回退、按钮权限 price:template:* 已定义下线，
--   操作手册 §2.9 亦标注「已下线」。但部分环境仍残留菜单 2063 及其按钮（前端页面调用的
--   /price/template/** 接口已不存在，点开即报错）。
--
-- 动作：删除残留菜单 2063 + 按钮 2065/2066/2067 + 角色-菜单关联（按 component/perms 兜底）。
-- 幂等：DELETE 条件删除，可重复执行。
-- 注：init_all.sql 全新初始化时由 [32]（w01）负责删除，本脚本用于已存在环境的残留清理。
-- =============================================================================

SET NAMES utf8mb4;

-- 1) 角色-菜单关联（防悬挂）
DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (2063, 2065, 2066, 2067);

DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (SELECT `menu_id` FROM `sys_menu`
                    WHERE `component` = 'price/template/index' OR `perms` LIKE 'price:template:%');

-- 2) 菜单 + 按钮
DELETE FROM `sys_menu`
WHERE `menu_id` IN (2063, 2065, 2066, 2067);

DELETE FROM `sys_menu`
WHERE `component` = 'price/template/index' OR `perms` LIKE 'price:template:%';

-- 3) 排序补齐：商品报价由 10 → 8（原 8/9 为报价模板及其占位）
UPDATE `sys_menu` SET `order_num` = 8, `update_by` = 'system', `update_time` = NOW()
WHERE `component` = 'product/quote/index';

-- 自检：
-- SELECT COUNT(*) AS left_menus FROM sys_menu WHERE component = 'price/template/index' OR perms LIKE 'price:template:%';
-- SELECT menu_id, menu_name, order_num FROM sys_menu WHERE parent_id = 4 ORDER BY order_num;

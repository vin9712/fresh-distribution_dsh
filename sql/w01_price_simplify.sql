-- ============================================================
-- w01_price_simplify.sql  |  W0-1 价格口径简化（客户端化ERP优化蓝图 §2/§8-S0-1）
-- ============================================================
-- 业务决策（2026-08-26 确认）：
--   价格层级取消「配送点覆盖价」与「报价模板」，订单仅使用客户正式报价；
--   未命中报价时由文员手工定价（原因必填、留审计）。
-- 影响：
--   1) 报价模板三表（price_template / price_template_sku / price_template_customer）
--      从未承载正式业务数据，随取价链路简化一并删除；
--   2) delivery_point_price 为 S2-2 遗留的配送点报价表，后端已无任何引用，一并删除；
--      （delivery_sku_override 已由 s11 删除，本脚本补齐同层级的其余废弃对象）
--   3) 下线「报价模板」菜单及按钮权限。
-- 幂等：可重复执行。
-- ============================================================

-- 1) 删除报价模板体系表
DROP TABLE IF EXISTS `price_template_customer`;
DROP TABLE IF EXISTS `price_template_sku`;
DROP TABLE IF EXISTS `price_template`;
DROP TABLE IF EXISTS `delivery_point_price`;

-- 2) 下线「报价模板」菜单及按钮权限（menu_id 2063/2065/2066/2067）
DELETE FROM sys_menu WHERE component = 'price/template/index';
DELETE FROM sys_menu WHERE perms LIKE 'price:template:%';

-- 3) 清理角色-菜单关联（防悬挂）
DELETE FROM sys_role_menu WHERE menu_id NOT IN (SELECT menu_id FROM sys_menu);

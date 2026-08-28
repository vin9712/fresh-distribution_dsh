-- ============================================================
-- s14d_delivery_ui_menu.sql — S14/T7 第二轮 前端配套菜单
-- 设计: docs/01-design/订单-送货-验收链路详细设计.md §6.1/§八
-- 内容:
--   1. 客户日总表菜单（order/batch/view，内部配货/采购视图 D-027/28）
--   2. 订单调整按钮停用（S14 §5.6 OrderAdjustment 退役节奏：写入口隐藏→接口标废→下版清理；
--      真实退货改走退货单 /order/return，补货走新增销售订单 D-030/D-032）
-- 幂等: 全部可重复执行（WHERE NOT EXISTS / 条件 UPDATE）
-- ============================================================

-- ---------- 1. 客户日总表菜单（挂在订单管理目录下 = 验收单同级） ----------
SET @order_parent := (SELECT parent_id FROM sys_menu WHERE component = 'order/acceptance/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '客户日总表', @order_parent, 5, 'batch', 'order/batch/view', NULL, '', 1, 0, 'C', '0', '0', 'order:delivery:batch', 'shopping', 'admin', sysdate(), '客户日总表(标准品名+总量+各点小计,无价格,内部配货采购视图 D-027/28)'
WHERE @order_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE component = 'order/batch/view');

-- ---------- 2. 订单调整按钮停用（写入口下线，菜单保留只读位便于审计回溯） ----------
UPDATE sys_menu SET status = '1', remark = 'S14 退役：写入口下线(§5.6)，退货走退货单 return:list，补货走新增销售订单'
WHERE menu_type = 'F' AND perms = 'order:sale:adjust' AND status <> '1';

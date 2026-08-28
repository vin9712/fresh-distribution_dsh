-- ============================================================
-- s15_order_menu_reorder.sql — 单据管理(menu_id=5)子菜单排序
-- 背景: 销售订单(2030)与送货单据(2036) order_num 均为 1，并列导致排序不稳定
-- 目标: 销售订单置为第一位，其余按 业务链路顺序 排列
-- 幂等: UPDATE 为绝对值赋值，可重复执行
-- ============================================================

UPDATE sys_menu SET order_num = 1 WHERE menu_id = 2030;  -- 销售订单
UPDATE sys_menu SET order_num = 2 WHERE menu_id = 2036;  -- 送货单据
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 2081;  -- 验收单
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 2106;  -- 退货单
UPDATE sys_menu SET order_num = 5 WHERE menu_id = 2116;  -- 客户日总表

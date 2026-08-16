-- ============================================================
-- S5-1 菜单/权限：送货单打印、送达按钮
-- F 按钮挂在"送货单据"(menu_id=2036) 下
-- ============================================================

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2079, '送货单打印', 2036, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:print', '#', 'admin', sysdate(), '', NULL, '送货单打印（print_count+1）'),
(2080, '送货单送达', 2036, 7, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:deliver', '#', 'admin', sysdate(), '', NULL, '送货单送达（订单→DELIVERED）');

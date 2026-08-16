-- ============================================================
-- S3-1 订单五状态：撤回、月结按钮权限
-- ============================================================
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2071, '订单撤回', 2030, 6, '', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:recall', '#', 'admin', sysdate(), '', NULL, ''),
(2072, '订单月结', 2030, 7, '', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:settle', '#', 'admin', sysdate(), '', NULL, '');

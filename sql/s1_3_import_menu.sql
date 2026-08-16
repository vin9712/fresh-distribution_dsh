-- ============================================================
-- S1-3 导入初始化：商品库导入、客户报价导入的按钮权限
-- ============================================================

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2061, '商品库导入', 2006, 6, '', '', NULL, '', 1, 0, 'F', '0', '0', 'product:spu:import', '#', 'admin', sysdate(), '', NULL, ''),
(2062, '报价导入', 2024, 6, '', '', NULL, '', 1, 0, 'F', '0', '0', 'product:quote:import', '#', 'admin', sysdate(), '', NULL, '');

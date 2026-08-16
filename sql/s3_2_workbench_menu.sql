-- ============================================================
-- S3-2 工作台菜单（一级菜单，置顶）
-- ============================================================
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2073, '工作台', 0, 1, 'workbench', 'workbench/index', NULL, '', 1, 0, 'C', '0', '0', '', 'dashboard', 'admin', sysdate(), '', NULL, '文员工作台');

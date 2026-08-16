-- ============================================================
-- S6-1 报表：菜单/按钮权限（报表中心）
-- C 菜单 2087 顶级；按钮 2088 查询 / 2089 导出
-- ============================================================

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2087, '报表中心', 0, 4, 'report', 'report/index', NULL, '', 1, 0, 'C', '0', '0', 'report:list', 'chart', 'admin', sysdate(), '', NULL, '报表中心菜单');

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2088, '报表查询', 2087, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'report:query', '#', 'admin', sysdate(), '', NULL, ''),
(2089, '报表导出', 2087, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'report:export', '#', 'admin', sysdate(), '', NULL, '');

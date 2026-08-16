-- ============================================================
-- S4 采购切片：菜单/按钮权限
-- 一级菜单 2075 采购管理；按钮 2076-2078（新增/修改/删除）。
-- 说明：生成采购单复用"新增"权限（purchase:add），不单独建按钮，
--       故 2079 预留未使用。
-- ============================================================

-- 一级菜单（C）：采购管理
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2075, '采购管理', 0, 3, 'purchase', 'purchase/index', NULL, '', 1, 0, 'C', '0', '0', 'purchase:list', 'shopping', 'admin', sysdate(), '', NULL, '采购管理菜单');

-- 按钮（F）：采购单
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2076, '采购单新增', 2075, 1, '', '', NULL, '', 1, 0, 'F', '0', '0', 'purchase:add', '#', 'admin', sysdate(), '', NULL, ''),
(2077, '采购单修改', 2075, 2, '', '', NULL, '', 1, 0, 'F', '0', '0', 'purchase:edit', '#', 'admin', sysdate(), '', NULL, ''),
(2078, '采购单删除', 2075, 3, '', '', NULL, '', 1, 0, 'F', '0', '0', 'purchase:remove', '#', 'admin', sysdate(), '', NULL, '');

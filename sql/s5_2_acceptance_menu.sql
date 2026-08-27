-- ============================================================
-- S5-2 验收：菜单/按钮权限 + 验收状态字典
-- C 菜单挂在"单据管理"(menu_id=5) 下；按钮 2082-2086
-- ============================================================

-- 验收单状态字典（0草稿 1已提交）
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, update_by, update_time, remark)
VALUES ('验收单状态', 't_acceptance_status', '0', 'admin', sysdate(), '', NULL, '验收单状态');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(0, '草稿',   '0', 't_acceptance_status', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '验收单状态-草稿'),
(1, '已提交', '1', 't_acceptance_status', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, '验收单状态-已提交');

-- 菜单
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2081, '验收单', 5, 3, 'acceptance', 'order/acceptance/index', NULL, '', 1, 0, 'C', '0', '0', 'acceptance:list', 'post', 'admin', sysdate(), '', NULL, '验收单菜单');

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2082, '验收单查询', 2081, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'acceptance:query', '#', 'admin', sysdate(), '', NULL, ''),
(2083, '验收单新增', 2081, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'acceptance:add', '#', 'admin', sysdate(), '', NULL, ''),
(2084, '验收单修改', 2081, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'acceptance:edit', '#', 'admin', sysdate(), '', NULL, ''),
(2085, '验收单删除', 2081, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'acceptance:remove', '#', 'admin', sysdate(), '', NULL, ''),
(2086, '验收单提交', 2081, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'acceptance:submit', '#', 'admin', sysdate(), '', NULL, '');

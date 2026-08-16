-- ============================================================
-- S2-2 菜单/权限：报价模板、配送点报价
-- 父菜单挂载在"基础信息"(menu_id=4) 下；menu_id 从 2063 起
-- ============================================================

-- 父菜单（C）：报价模板
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2063, '报价模板', 4, 3, 'priceTemplate', 'price/template/index', NULL, '', 1, 0, 'C', '0', '0', 'price:template:list', '#', 'admin', sysdate(), '', NULL, '报价模板菜单');

-- 按钮（F）：报价模板
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2065, '模板新增', 2063, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:template:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2066, '模板修改', 2063, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:template:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2067, '模板删除', 2063, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:template:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 父菜单（C）：配送点报价
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2064, '配送点报价', 4, 4, 'pointPrice', 'price/pointPrice/index', NULL, '', 1, 0, 'C', '0', '0', 'price:point:list', '#', 'admin', sysdate(), '', NULL, '配送点报价菜单');

-- 按钮（F）：配送点报价
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2068, '配送点新增', 2064, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:point:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2069, '配送点修改', 2064, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:point:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2070, '配送点删除', 2064, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:point:remove', '#', 'admin', sysdate(), '', NULL, '');

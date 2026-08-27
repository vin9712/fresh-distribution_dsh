-- ============================================================
-- R1 阶段4 前端配套：菜单/权限/字典
-- 1) 菜单 2090 起（现有最大值 2089）
-- 2) 配送点报价(2064) 改名为"配送点覆盖"，权限 price:point:* → price:delivery-override:*
-- 3) 补齐 t_sku_unit / biz_yes_no 字典数据（字典类型已存在但无数据）
-- 幂等：可重复执行（先删后插）
-- ============================================================

-- ---------- 1. 配送点报价 → 配送点覆盖（改名 + 换权限） ----------
UPDATE `sys_menu` SET menu_name = '配送点覆盖', perms = 'price:delivery-override:list', remark = '配送点覆盖菜单（替代原配送点报价）' WHERE menu_id = 2064;
UPDATE `sys_menu` SET menu_name = '配送点覆盖新增', perms = 'price:delivery-override:add' WHERE menu_id = 2068;
UPDATE `sys_menu` SET menu_name = '配送点覆盖修改', perms = 'price:delivery-override:edit' WHERE menu_id = 2069;
UPDATE `sys_menu` SET menu_name = '配送点覆盖删除', perms = 'price:delivery-override:remove' WHERE menu_id = 2070;

-- ---------- 2. 客户商品菜单（挂"基础信息" parent=4） ----------
DELETE FROM `sys_menu` WHERE menu_id BETWEEN 2090 AND 2094;
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2090, '客户商品', 4, 5, 'customerSku', 'product/customerSku/index', NULL, '', 1, 0, 'C', '0', '0', 'product:customer-sku:list', '#', 'admin', sysdate(), '', NULL, '客户商品池与个性化');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2091, '客户商品新增', 2090, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:customer-sku:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2092, '客户商品修改', 2090, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:customer-sku:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2093, '客户商品删除', 2090, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:customer-sku:remove', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2094, '客户商品批量赋值', 2090, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:customer-sku:assign', '#', 'admin', sysdate(), '', NULL, '');

-- ---------- 3. 默认SKU模板菜单 ----------
DELETE FROM `sys_menu` WHERE menu_id BETWEEN 2095 AND 2099;
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2095, '默认SKU模板', 4, 6, 'defaultSkuTemplate', 'product/defaultSkuTemplate/index', NULL, '', 1, 0, 'C', '0', '0', 'product:default-sku-template:list', '#', 'admin', sysdate(), '', NULL, '批量赋值默认SKU模板');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2096, '模板查询', 2095, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:default-sku-template:query', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2097, '模板新增', 2095, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:default-sku-template:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2098, '模板修改', 2095, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:default-sku-template:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2099, '模板删除', 2095, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:default-sku-template:remove', '#', 'admin', sysdate(), '', NULL, '');

-- ---------- 4. 字典数据补齐（幂等：先删后插） ----------
-- biz_yes_no（业务是否）：0=否 1=是
DELETE FROM `sys_dict_data` WHERE dict_type = 'biz_yes_no';
INSERT INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`) VALUES (52, 1, '是', '1', 'biz_yes_no', '', 'primary', 'N', '0', 'admin', sysdate(), '');
INSERT INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`) VALUES (53, 2, '否', '0', 'biz_yes_no', '', 'danger', 'N', '0', 'admin', sysdate(), '');
-- t_sku_unit（商品单位）
DELETE FROM `sys_dict_data` WHERE dict_type = 't_sku_unit';
INSERT INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`) VALUES
(54, 1, '斤', '斤', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(55, 2, '公斤', '公斤', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(56, 3, '箱', '箱', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(57, 4, '袋', '袋', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(58, 5, '份', '份', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(59, 6, '个', '个', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(60, 7, '包', '包', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(61, 8, '瓶', '瓶', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(62, 9, '件', '件', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(63, 10, '捆', '捆', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), '');

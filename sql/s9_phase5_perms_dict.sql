-- ============================================================
-- S9 Phase5：权限/字典一致性修复（方案 §3.6）
-- 1) 配送点页权限 partner:customerDept:*（补 sys_menu 权限项，供角色分配）
-- 2) 客户类型字典 t_customer_type 补充字典数据（此前只有 dict_type 无 dict_data）
-- 3) 清理 price:point:* 旧权限残留（已被 price:delivery-override:* 取代）
-- menu_id 从 2100 起（当前最大 2099）；均为幂等写法，可重复执行
-- ============================================================

-- ---------- 1. 配送点页权限（挂载到"客户信息" menu_id=2012 下） ----------
INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2100, '配送点查询', 2012, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customerDept:query', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2101, '配送点新增', 2012, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customerDept:add', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2102, '配送点修改', 2012, 7, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customerDept:edit', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2103, '配送点删除', 2012, 8, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customerDept:remove', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2104, '配送点导出', 2012, 9, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customerDept:export', '#', 'admin', sysdate(), '', NULL, '');

-- partner:customerDept:list（列表接口权限，供角色分配；配送点页为隐藏路由，无独立 C 菜单）
-- 说明：配送点页入口走 /basicInfo/customer-dept（partner:customer:list），此处补 list 便于角色细粒度授权
INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2105, '配送点列表', 2012, 10, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customerDept:list', '#', 'admin', sysdate(), '', NULL, '');

-- ---------- 2. 客户类型字典 t_customer_type 补充数据 ----------
-- 若已存在该 dict_type+dict_value 则跳过
INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 1, '餐馆', '1', 't_customer_type', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 't_customer_type' AND `dict_value` = '1');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2, '食堂', '2', 't_customer_type', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 't_customer_type' AND `dict_value` = '2');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 3, '商超', '3', 't_customer_type', '', 'warning', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 't_customer_type' AND `dict_value` = '3');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 4, '商户', '4', 't_customer_type', '', 'info', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 't_customer_type' AND `dict_value` = '4');

-- ---------- 3. 清理 price:point:* 旧权限残留（已被 price:delivery-override:* 取代） ----------
DELETE FROM `sys_menu` WHERE `perms` LIKE 'price:point:%';

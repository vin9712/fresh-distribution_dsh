-- ============================================================
-- S3-4 菜单/权限：订单调整（配送后加退换）
-- F 按钮挂在"销售订单"(menu_id=2030) 下
-- ============================================================

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2074, '订单调整', 2030, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:adjust', '#', 'admin', sysdate(), '', NULL, '');

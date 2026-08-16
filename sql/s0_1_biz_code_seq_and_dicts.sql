-- ============================================================
-- S0-1 权限字典与单号服务
-- 1) 通用业务序列表 biz_code_seq（DB 序列替代 Redis，Redis 降为非硬依赖）
-- 2) 补齐业务状态字典数据（原库仅有 dict_type 无 dict_data，页面此前显示裸数字）
-- 3) 新增采购单状态字典
-- ============================================================

-- 1. 通用业务序列表
CREATE TABLE IF NOT EXISTS `biz_code_seq` (
  `biz_key`     varchar(64) NOT NULL COMMENT '序列键（bizType:yyyyMMdd 或 bizType:ownerId）',
  `seq`         bigint(20)  NOT NULL DEFAULT 0 COMMENT '当前序列值',
  `update_time` datetime    DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用业务序列表';

-- 2. 销售订单状态字典数据（dict_type=104，枚举码 0-4 保持不变，仅补数据与文案）
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(30, 0, '草稿',   '0', 't_sale_order_status', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '订单状态-草稿(DRAFT)'),
(31, 1, '已确认', '1', 't_sale_order_status', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, '订单状态-已确认(CONFIRMED)'),
(32, 2, '已配送', '2', 't_sale_order_status', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, '订单状态-已配送(DELIVERED)'),
(33, 3, '已验收', '3', 't_sale_order_status', '', 'warning', 'N', '0', 'admin', sysdate(), '', NULL, '订单状态-已验收(ACCEPTED)'),
(34, 4, '已结算', '4', 't_sale_order_status', '', 'danger',  'N', '0', 'admin', sysdate(), '', NULL, '订单状态-已结算(SETTLED)');

-- 3. 送货单状态字典数据（dict_type=107，枚举码 0-2 不变）
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(35, 0, '待打印', '0', 't_delivery_order_status', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '送货单状态-待打印'),
(36, 1, '已打印', '1', 't_delivery_order_status', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, '送货单状态-已打印'),
(37, 2, '已送达', '2', 't_delivery_order_status', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, '送货单状态-已送达');

-- 4. 报价状态字典数据（dict_type=103，沿用现状 NEW/PUBLISHED/INVALID）
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(38, 0, '新增', '0', 't_sku_quote_status', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '报价状态-新增'),
(39, 1, '发布', '1', 't_sku_quote_status', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, '报价状态-发布'),
(40, 2, '失效', '2', 't_sku_quote_status', '', 'danger',  'N', '0', 'admin', sysdate(), '', NULL, '报价状态-失效');

-- 5. 销售订单类型/来源字典数据（dict_type=105/106）
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(41, 0, '正常订单', '1', 't_sale_order_type', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, '订单类型-正常订单'),
(42, 1, '加单',     '2', 't_sale_order_type', '', 'warning', 'N', '0', 'admin', sysdate(), '', NULL, '订单类型-加单'),
(43, 0, '后台下单', '1', 't_sale_order_source', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, '订单来源-后台下单'),
(44, 1, '线上下单', '2', 't_sale_order_source', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '订单来源-线上下单');

-- 6. 采购单状态字典（新增 dict_type=108）
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, update_by, update_time, remark)
VALUES (108, '采购单状态', 't_purchase_order_status', '0', 'admin', sysdate(), '', NULL, '采购单状态');

INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(45, 0, '草稿',   '0', 't_purchase_order_status', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '采购单状态-草稿'),
(46, 1, '已确认', '1', 't_purchase_order_status', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, '采购单状态-已确认'),
(47, 2, '已入库', '2', 't_purchase_order_status', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, '采购单状态-已入库');

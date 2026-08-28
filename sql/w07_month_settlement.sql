-- =====================================================================
-- W0-3.1 按客户月结：新增客户月度结算表 t_month_settlement + 菜单权限
-- 内容：1) 建表（客户+结算月唯一）；2) 菜单 + 按钮权限（monthSettlement:list/settle）
-- 幂等：CREATE TABLE IF NOT EXISTS / WHERE NOT EXISTS；init_all.sql 已同步建表。
-- 回滚参考：DROP TABLE IF EXISTS `t_month_settlement`；删除对应 sys_menu。
-- =====================================================================

-- ---------- 1. 建表 ----------
CREATE TABLE IF NOT EXISTS `t_month_settlement` (
  `id`           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `customer_id`  bigint(20)   NOT NULL COMMENT '客户ID',
  `bill_month`   varchar(7)   NOT NULL COMMENT '结算月份（yyyy-MM）',
  `status`       tinyint(3)   NOT NULL DEFAULT 0 COMMENT '状态：0未结 1已结',
  `settled_by`   varchar(64)  DEFAULT '' COMMENT '结算人',
  `settled_time` datetime     DEFAULT NULL COMMENT '结算时间',
  `remark`       varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_month` (`customer_id`, `bill_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户月度结算(W0-3.1)';

-- ---------- 2. 菜单 + 按钮权限（挂在订单管理目录下） ----------
SET @order_parent := (SELECT parent_id FROM sys_menu WHERE component = 'order/acceptance/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '客户月结', @order_parent, 7, 'settlement', 'order/settlement/index', NULL, '', 1, 0, 'C', '0', '0', 'monthSettlement:list', 'money', 'admin', sysdate(), '按客户月结：预览验收单+下月调整单,导出,月结后冻结该客户该月数据'
WHERE @order_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE component = 'order/settlement/index');

SET @settle_menu := (SELECT menu_id FROM sys_menu WHERE component = 'order/settlement/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '客户月结查询', @settle_menu, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'monthSettlement:list', '#', 'admin', sysdate(), ''
WHERE @settle_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @settle_menu AND perms = 'monthSettlement:list');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '客户月结执行', @settle_menu, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'monthSettlement:settle', '#', 'admin', sysdate(), ''
WHERE @settle_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @settle_menu AND perms = 'monthSettlement:settle');

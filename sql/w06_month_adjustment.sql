-- =====================================================================
-- W0-2.7 下月调整单（monthAdjustment）：独立单号、草稿/已提交、应收与采购成本分项留痕
-- 内容：1) 建表 t_month_adjustment；2) 菜单 + 按钮权限（monthAdjustment:list/add/edit/remove）
-- 幂等：CREATE TABLE IF NOT EXISTS / WHERE NOT EXISTS；init_all.sql 已同步建表。
-- 回滚参考：DROP TABLE IF EXISTS `t_month_adjustment`；删除对应 sys_menu。
-- =====================================================================

-- ---------- 1. 建表 ----------
CREATE TABLE IF NOT EXISTS `t_month_adjustment` (
  `id`                   bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`                 varchar(32)   NOT NULL COMMENT '调整单号（TJyyyyMMddNNN，独立序列）',
  `customer_id`          bigint(20)    NOT NULL COMMENT '客户ID',
  `bill_month`           varchar(7)    NOT NULL COMMENT '结算所属月份（yyyy-MM）',
  `receivable_amount`    decimal(12,2) NOT NULL DEFAULT 0 COMMENT '应收金额调整（可正可负）',
  `purchase_cost_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '采购成本调整（可正可负）',
  `status`               tinyint(3)    NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1已提交',
  `remark`               varchar(500)  DEFAULT NULL COMMENT '备注',
  `is_deleted`           tinyint(3)    NOT NULL DEFAULT 0 COMMENT '逻辑删除（0正常 1删除）',
  `create_by`            varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`          datetime      DEFAULT NULL COMMENT '创建时间',
  `update_by`            varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`          datetime      DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_customer_month` (`customer_id`, `bill_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='下月调整单(W0-2.7)';

-- ---------- 2. 菜单 + 按钮权限（挂在订单管理目录下） ----------
SET @order_parent := (SELECT parent_id FROM sys_menu WHERE component = 'order/acceptance/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '下月调整单', @order_parent, 6, 'monthAdjustment', 'order/monthAdjustment/index', NULL, '', 1, 0, 'C', '0', '0', 'monthAdjustment:list', 'edit', 'admin', sysdate(), '下月调整单(独立单号/草稿提交/应收与采购成本分项留痕,月结后纠错不改写原订单快照)'
WHERE @order_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE component = 'order/monthAdjustment/index');

SET @madj_menu := (SELECT menu_id FROM sys_menu WHERE component = 'order/monthAdjustment/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '下月调整单查询', @madj_menu, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'monthAdjustment:list', '#', 'admin', sysdate(), ''
WHERE @madj_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @madj_menu AND perms = 'monthAdjustment:list');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '下月调整单新增', @madj_menu, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'monthAdjustment:add', '#', 'admin', sysdate(), ''
WHERE @madj_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @madj_menu AND perms = 'monthAdjustment:add');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '下月调整单修改', @madj_menu, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'monthAdjustment:edit', '#', 'admin', sysdate(), ''
WHERE @madj_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @madj_menu AND perms = 'monthAdjustment:edit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '下月调整单删除', @madj_menu, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'monthAdjustment:remove', '#', 'admin', sysdate(), ''
WHERE @madj_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @madj_menu AND perms = 'monthAdjustment:remove');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '下月调整单提交', @madj_menu, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'monthAdjustment:edit', '#', 'admin', sysdate(), ''
WHERE @madj_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @madj_menu AND perms = 'monthAdjustment:edit');

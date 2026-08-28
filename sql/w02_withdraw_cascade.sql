-- ============================================================
-- W0-2.1 撤回已确认订单 → 级联扣除/作废未打印采购单与送货单
-- 幂等脚本，可重复执行
--
-- 背景（蓝图「撤回级联/共享单据撤回/空关联单据/作废审计」）：
--   撤回已确认订单时，自动扣除其未执行关联单据并重算：
--   - 送货单：仅待打印（PENDING）单参与级联；软删该订单来源分配（source_item），
--     有剩余分配的聚合行重算，无明细的空单自动作废；
--   - 采购单：未入库（草稿/已确认）自动采购单按汇总键扣除数量并重算，
--     无明细的空单自动作废；已入库单视同已执行，拒撤。
--
-- 改造：
--   1. purchase_order 增加作废审计三列（void_reason/void_by/void_time），
--      采购状态机增加 3=已作废（代码侧 PurchaseOrderStatus.VOIDED）；
--   2. 字典 delivery_void_reason 增加 order_withdraw「订单撤回」，
--      供送货单空单自动作废原因与前端展示对齐（作废单列表筛选/追溯）。
--
-- 回滚：
--   ALTER TABLE purchase_order DROP COLUMN void_reason, DROP COLUMN void_by, DROP COLUMN void_time;
--   DELETE FROM sys_dict_data WHERE dict_type='delivery_void_reason' AND dict_value='order_withdraw';
-- ============================================================

-- ---------- 1. purchase_order 作废审计列（幂等：存在即跳过） ----------
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'purchase_order'
      AND COLUMN_NAME = 'void_reason'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `purchase_order` ADD COLUMN `void_reason` varchar(200) DEFAULT NULL COMMENT ''作废原因'' AFTER `status`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'purchase_order'
      AND COLUMN_NAME = 'void_by'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `purchase_order` ADD COLUMN `void_by` varchar(64) DEFAULT '''' COMMENT ''作废人'' AFTER `void_reason`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'purchase_order'
      AND COLUMN_NAME = 'void_time'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `purchase_order` ADD COLUMN `void_time` datetime DEFAULT NULL COMMENT ''作废时间'' AFTER `void_by`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. 字典 delivery_void_reason 增加「订单撤回」 ----------
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 6, '订单撤回', 'order_withdraw', 'delivery_void_reason', '', 'info', 'N', '0', 'admin', sysdate(), '撤回已确认订单时，级联扣除后无明细的送货单自动作废（系统动作，非人工选择）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_void_reason' AND `dict_value` = 'order_withdraw');

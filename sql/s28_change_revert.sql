-- ============================================================
-- S28 变更回退留痕（OA，《订单页一键验收链路设计》§4.4 回退）：
-- markReturned 将 num/actual_num 清零（应送0口径），原应收数量转入本列留痕，
-- 供「回退」恢复（change_type 还原 + num/actual_num 取快照）。
--
-- 幂等：information_schema 探测 + PREPARE（MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS）。
-- ============================================================

SET NAMES utf8mb4;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_sale_order_detail' AND COLUMN_NAME = 'change_original_num'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_sale_order_detail`
        ADD COLUMN `change_original_num` DECIMAL(12,2) NULL
            COMMENT ''配送后变更回退用：标记退货/换货前的原应收数量'' AFTER `change_remark`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 自检：
-- SELECT column_name, column_type, is_nullable FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 't_sale_order_detail' AND column_name = 'change_original_num';

-- ============================================================
-- Phase 2：销售订单列表页生成采购单/送货单抽屉
-- 1) purchase_order 增加采购员字段（幂等：information_schema 探测后 ALTER）
-- 说明：MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，用 PREPARE 兼容幂等重跑
-- ============================================================

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'purchase_order'
      AND COLUMN_NAME = 'purchaser'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE purchase_order ADD COLUMN purchaser varchar(64) DEFAULT NULL COMMENT ''采购员'' AFTER supplier_name',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

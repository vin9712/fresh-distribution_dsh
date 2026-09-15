-- ============================================================
-- S30 采购单重设计：日应采汇总 + 分批成本录入
--   设计依据：docs/01-design/采购单日应采汇总与分批成本录入设计.md（D-056 ~ D-063）
--
--   A 段（必执行）：purchase_item 行改为「一次实际进货批次」，加批次列；
--                  purchase_order 退役 source_type / source_order_ids
--                  （撤回级联改按 order_date 反查当日采购单）。
--   B 段（注释保护，人工确认后执行）：清空历史采购数据——开发期重构，
--                  旧「应送量 × 销售价」脏数据不再进入成本统计。
--
--   幂等：information_schema 探测 + PREPARE（MySQL 5.7 不支持 ADD/DROP COLUMN IF EXISTS）。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- A-1. purchase_item 加批次列
-- ------------------------------------------------------------

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_item' AND COLUMN_NAME = 'batch_no'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `purchase_item`
        ADD COLUMN `batch_no` int(10) NOT NULL DEFAULT 1 COMMENT ''同商品组内批次序号（1,2,3…）'' AFTER `purchase_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_item' AND COLUMN_NAME = 'supplier_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `purchase_item`
        ADD COLUMN `supplier_id` bigint(20) DEFAULT NULL COMMENT ''本批次供应商ID（可空）'' AFTER `sku_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_item' AND COLUMN_NAME = 'supplier_name'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `purchase_item`
        ADD COLUMN `supplier_name` varchar(200) DEFAULT NULL COMMENT ''本批次供应商名称（直填）'' AFTER `supplier_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_item' AND COLUMN_NAME = 'required_qty'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `purchase_item`
        ADD COLUMN `required_qty` decimal(12,2) DEFAULT NULL COMMENT ''录入时快照的应采数量（差异审计用）'' AFTER `quantity`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_item' AND COLUMN_NAME = 'create_by'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `purchase_item`
        ADD COLUMN `create_by` varchar(64) DEFAULT '''' COMMENT ''录入人'' AFTER `sort`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_item' AND COLUMN_NAME = 'create_time'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `purchase_item`
        ADD COLUMN `create_time` datetime DEFAULT NULL COMMENT ''录入时间'' AFTER `create_by`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_item' AND COLUMN_NAME = 'remark'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `purchase_item`
        ADD COLUMN `remark` varchar(500) DEFAULT NULL COMMENT ''批次备注（发票号/车次等）'' AFTER `create_time`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- A-2. purchase_order 退役 source_type / source_order_ids
--      （撤回级联改按 order_date 反查当日非作废采购单）
-- ------------------------------------------------------------

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'source_type'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE `purchase_order` DROP COLUMN `source_type`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'source_order_ids'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE `purchase_order` DROP COLUMN `source_order_ids`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- A-3. 并发防重建当日单：active_date 生成列 + 唯一索引
--      active_date = if(status=3, NULL, order_date)：作废单为 NULL 不占位，
--      同一天允许「作废后重建」，但非作废单全局唯一（应用层捕获 DuplicateKeyException 后回查）。
--      ⚠ 若历史数据存在同日多张非作废采购单，需先清理/作废多余单后再执行唯一索引。
-- ------------------------------------------------------------

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'active_date'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `purchase_order`
        ADD COLUMN `active_date` date GENERATED ALWAYS AS (if(`status` = 3, NULL, `order_date`)) STORED COMMENT ''并发防重生成列（=order_date；作废单为 NULL）'' AFTER `void_time`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND INDEX_NAME = 'uk_purchase_order_active_date'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `purchase_order` ADD UNIQUE KEY `uk_purchase_order_active_date` (`active_date`)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- B 段（⚠ 注释保护，需人工确认后执行）：
--   开发期重构，清空历史采购数据，避免旧「应送量 × 销售价」脏数据进入成本统计。
--   确认无需要的采购历史后，取消注释逐条执行。
-- ------------------------------------------------------------
-- DELETE FROM `purchase_modify_log`;
-- DELETE FROM `purchase_item`;
-- DELETE FROM `purchase_order`;

-- 自检：
-- SELECT column_name, column_type, is_nullable FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'purchase_item' ORDER BY ordinal_position;
-- SELECT column_name FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'purchase_order'
--    AND column_name IN ('source_type','source_order_ids');   -- 期望 0 行
-- SELECT index_name FROM information_schema.statistics
--  WHERE table_schema = DATABASE() AND table_name = 'purchase_order'
--    AND index_name = 'uk_purchase_order_active_date';        -- 期望 1 行

-- =============================================================================
-- s20：D-055 验收维度改造——验收单挂 客户+日期+配送点，应送行=订单明细
--
-- 口径（方案乙，一维一验）：
--   验收 = 客户 + 配送日期 + 配送点（AcceptanceService#createByCustomerPoint）；
--   应送行 = 订单明细行（含加单/换货/退货标记），实收由文员录入；
--   acceptance.delivery_order_id 自 D-055 起不再写入（历史单保留兼容，列改可空）。
--
-- 幂等：information_schema 探测 + PREPARE。执行前请全库备份（sql/db_bak/）。
-- 注意：acceptance 上既有唯一键 uk_delivery_order_id 在列改为可空后仍然安全——
--       MySQL 唯一索引允许多个 NULL，D-055 新验收单该列为空，不会互相冲突。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- 1. acceptance.delivery_order_id 改可空（历史兼容列） ----------
SET @is_nullable := (
    SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance' AND COLUMN_NAME = 'delivery_order_id'
);
SET @ddl := IF(@is_nullable = 'NO',
    'ALTER TABLE `acceptance`
        MODIFY COLUMN `delivery_order_id` bigint unsigned DEFAULT NULL
            COMMENT ''送货单ID（D-055 后不再使用，保留历史兼容）''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. acceptance.delivery_date（验收维度：客户+日期+点） ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance' AND COLUMN_NAME = 'delivery_date'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `acceptance`
        ADD COLUMN `delivery_date` date DEFAULT NULL
            COMMENT ''配送日期（D-055 验收维度=客户+日期+点）'' AFTER `customer_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. acceptance_item.sale_order_detail_id（应送行=订单明细） ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance_item' AND COLUMN_NAME = 'sale_order_detail_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `acceptance_item`
        ADD COLUMN `sale_order_detail_id` bigint unsigned DEFAULT NULL
            COMMENT ''来源订单明细ID（D-055 应送行=订单明细，含加单/换货/退货标记）'' AFTER `delivery_item_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 4. t_return_item.sale_order_detail_id（退货行对齐同一来源维度） ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_return_item' AND COLUMN_NAME = 'sale_order_detail_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_return_item`
        ADD COLUMN `sale_order_detail_id` bigint unsigned DEFAULT NULL
            COMMENT ''来源订单明细ID（D-055 与验收行同维度对齐）'' AFTER `acceptance_item_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 自检：
-- SELECT table_name, column_name, column_type, is_nullable FROM information_schema.columns
--  WHERE table_schema = DATABASE()
--    AND ((table_name='acceptance' AND column_name IN ('delivery_order_id','delivery_date'))
--      OR (table_name='acceptance_item' AND column_name='sale_order_detail_id')
--      OR (table_name='t_return_item' AND column_name='sale_order_detail_id'));

-- ============================================================
-- S27 订单维度验收（OA，《订单页一键验收链路设计》）：
-- 验收单维度新增「一订单一验」，交互迁移到订单明细页（mode=acceptance）。
-- 既有 客户日/历史送货单 维度验收单不受影响（sale_order_id 保持 NULL）。
--
-- 幂等：information_schema 探测 + PREPARE（MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS）。
-- 执行前请全库备份（sql/db_bak/）。
-- ============================================================

SET NAMES utf8mb4;

-- ---------- 1. acceptance.sale_order_id（订单维度验收：来源销售订单ID） ----------
-- 类型对齐主键 bigint unsigned（t_sale_order.id 同型）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance' AND COLUMN_NAME = 'sale_order_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `acceptance`
        ADD COLUMN `sale_order_id` BIGINT UNSIGNED NULL
            COMMENT ''订单维度验收：来源销售订单ID（一订单一验，OA；历史/客户日单为NULL）'' AFTER `delivery_order_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. 一订单一验唯一键 ----------
-- 唯一键仅约束非 NULL：MySQL 唯一索引允许多行 NULL，历史单/客户日单天然豁免
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance' AND INDEX_NAME = 'uk_acceptance_sale_order'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `acceptance` ADD UNIQUE KEY `uk_acceptance_sale_order` (`sale_order_id`)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 自检：
-- SELECT column_name, column_type, is_nullable FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'acceptance' AND column_name = 'sale_order_id';
-- SELECT index_name, non_unique FROM information_schema.statistics
--  WHERE table_schema = DATABASE() AND table_name = 'acceptance' AND index_name = 'uk_acceptance_sale_order';

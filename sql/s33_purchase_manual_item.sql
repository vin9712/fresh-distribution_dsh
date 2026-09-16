-- =============================================================================
-- s33：采购录入支持「新增商品」（SKU 库选品 / 临时商品）
--
-- 背景（2026-09-16）：采购录入页新增「新增商品」入口，允许录入当日应采清单之外的
--   SKU（商品库选品）或临时商品（无 SKU，仅品名/规格/单位）。
--   purchase_item 新增 is_manual 标记：
--     0 = 应采清单命中 / 订单已撤回但批次仍在（历史遗留，日汇总按“孤儿”只读展示）
--     1 = 手动新增商品 / 临时商品（日汇总中可继续录入批次，不标孤儿）
--
-- 幂等：先查 information_schema 再 ALTER，可重复执行。
-- 回滚参考：ALTER TABLE `purchase_item` DROP COLUMN `is_manual`;
-- =============================================================================

SET NAMES utf8mb4;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_item' AND COLUMN_NAME = 'is_manual'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `purchase_item`
        ADD COLUMN `is_manual` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否手动新增商品批次（0否=应采清单/订单撤回遗留，1是=新增商品或临时商品）'' AFTER `required_qty`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 自检：
-- SELECT COLUMN_NAME, COLUMN_DEFAULT, COLUMN_COMMENT FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_item' AND COLUMN_NAME = 'is_manual';

-- s11_customer_sku_dept_scoping.sql
-- 客户商品池增加配送点限定（白名单模型），替代并废弃 delivery_sku_override
--
-- 背景（2026-02 业务确认）：
--   员工食堂等场景是「不同 SKU 归属不同配送点」，不是同一 SKU 不同价。
--   价格差异通过同 SPU 下拆分不同 SKU 解决，报价引擎保持客户级不动。
-- 模型：
--   customers_sku.dept_id 为空     → 客户通用商品（所有配送点可见）
--   customers_sku.dept_id = 配送点 → 仅该配送点可见
--   下单选品范围 = 通用池 ∪ 本点专属池；不支持"全局可见但某点排除"
-- 废弃：
--   delivery_sku_override 表从未投入使用，直接删除；
--   「配送点覆盖」菜单下线。

-- 1) customers_sku 增加 dept_id（幂等：存在即跳过）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customers_sku' AND COLUMN_NAME = 'dept_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `customers_sku` ADD COLUMN `dept_id` BIGINT NULL COMMENT ''限定配送点ID(t_customer_dept.id)，空=客户通用'' AFTER `sku_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 索引（幂等）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customers_sku' AND INDEX_NAME = 'idx_customer_dept');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `customers_sku` ADD INDEX `idx_customer_dept` (`customer_id`, `dept_id`)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 删除未使用的配送点覆盖表
DROP TABLE IF EXISTS `delivery_sku_override`;

-- 4) 下线「配送点覆盖」菜单及按钮权限
DELETE FROM sys_menu WHERE component = 'price/pointPrice/index';
DELETE FROM sys_menu WHERE perms LIKE 'price:delivery-override:%';

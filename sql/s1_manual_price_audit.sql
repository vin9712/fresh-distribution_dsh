-- =====================================================================
-- S1-1.3 手工定价留痕：t_sale_order_detail 加 3 列
--   price_source  报价来源：quote=客户报价，manual=手工定价，temp=临时商品默认价；NULL=历史数据/未标注
--   ref_price     原建议价（取价引擎/报价快照价，手工定价时审计比对；无报价为 NULL）
--   price_reason  手工定价原因（S1-1.3；2026-09 起可选、不再必填，仅留痕）
-- 幂等：information_schema 探测后 ALTER；init_all.sql 已同步（CREATE TABLE + [35] 节）。
-- 回滚参考：ALTER TABLE t_sale_order_detail DROP COLUMN price_source, DROP COLUMN ref_price, DROP COLUMN price_reason;
-- =====================================================================

SET @has_price_source := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_sale_order_detail'
    AND COLUMN_NAME = 'price_source'
);

SET @ddl := IF(@has_price_source = 0,
  'ALTER TABLE `t_sale_order_detail`
     ADD COLUMN `price_source` varchar(16) DEFAULT NULL COMMENT ''报价来源（S1-1.3）：quote=客户报价,manual=手工定价,temp=临时商品默认价'' AFTER `expect_amount`',
  'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_ref_price := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_sale_order_detail'
    AND COLUMN_NAME = 'ref_price'
);

SET @ddl := IF(@has_ref_price = 0,
  'ALTER TABLE `t_sale_order_detail`
     ADD COLUMN `ref_price` decimal(10,2) DEFAULT NULL COMMENT ''原建议价（S1-1.3 手工定价审计比对；无报价为NULL）'' AFTER `price_source`',
  'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_price_reason := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_sale_order_detail'
    AND COLUMN_NAME = 'price_reason'
);

SET @ddl := IF(@has_price_reason = 0,
  'ALTER TABLE `t_sale_order_detail`
     ADD COLUMN `price_reason` varchar(200) DEFAULT NULL COMMENT ''手工定价原因（S1-1.3，2026-09 起可选、不再必填，仅留痕）'' AFTER `ref_price`',
  'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- W0-3.2 待验收提醒：送货单加「最近打印时间」列 print_time
-- 用途：打印满2小时仍未验收→黄；配送日当天11:30后→红。
-- 幂等：information_schema 探测后 ALTER；init_all.sql 已同步（t_delivery_order 加列）。
-- 回滚参考：ALTER TABLE t_delivery_order DROP COLUMN print_time;
-- =====================================================================

SET @has_print_time := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_delivery_order'
    AND COLUMN_NAME = 'print_time'
);

SET @ddl := IF(@has_print_time = 0,
  'ALTER TABLE `t_delivery_order` ADD COLUMN `print_time` datetime DEFAULT NULL COMMENT ''最近打印时间（W0-3.2 待验收提醒）'' AFTER `print_count`',
  'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

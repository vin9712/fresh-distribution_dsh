-- ============================================================
-- s36 客户配送点排序（总单列顺序）  |  2026-09-19
-- ------------------------------------------------------------
-- 背景：送货单据「矩阵总表」的列顺序原来固定按 tcd.code 排序，
--       无法按业务习惯调整（如按送货路线/装车顺序）。
-- 方案：t_customer_dept 增加 sort_no（小在前），矩阵列与配送点列表
--       均按 sort_no asc, code asc, id asc 排序；前端配送点页支持拖拽排序。
-- 幂等：重复执行安全（列已存在时 MySQL 8 会报 1060，可用下方判断跳过）。
-- ============================================================

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_customer_dept'
      AND COLUMN_NAME = 'sort_no'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE `t_customer_dept` ADD COLUMN `sort_no` int(10) NOT NULL DEFAULT 0 COMMENT ''排序（总单列顺序，小在前；0=未设置，排在已设置之后按编号）'' AFTER `valid`',
    'SELECT ''t_customer_dept.sort_no already exists'' AS msg'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

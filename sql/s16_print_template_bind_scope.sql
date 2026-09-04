-- =============================================================================
-- s16：打印模板绑定加维（P1/D-048，补齐入库版）
--
-- 背景：A 类总单 delivery_point_id=NULL 时，三级绑定解析靠「= NULL 恒不成立」巧合
--       回落全局默认，总单模板会悄悄命中点单模板。本脚本给 t_print_template 加打印形态
--       print_form（MATRIX=跨点总单矩阵 / FLAT=点单平铺），解析改为「形态 + 绑定层级」。
--
-- 幂等：information_schema 探测 + PREPARE，可重复执行。执行前请备份（sql/db_bak/）。
-- 关联：模板数据见 s18；D-055 后送货单为订单视图，本列仍用于打印模板选型（历史单证链路）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- 1. t_print_template.print_form ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_print_template' AND COLUMN_NAME = 'print_form'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_print_template`
        ADD COLUMN `print_form` varchar(16) NOT NULL DEFAULT ''FLAT''
            COMMENT ''打印形态：MATRIX=跨点总单矩阵 / FLAT=点单平铺（P1/D-048）'' AFTER `bind_type`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. t_print_template_version.print_form（版本快照同形态） ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_print_template_version' AND COLUMN_NAME = 'print_form'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_print_template_version`
        ADD COLUMN `print_form` varchar(16) NOT NULL DEFAULT ''FLAT''
            COMMENT ''打印形态快照：MATRIX/FLAT（P1/D-048）'' AFTER `bind_type`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 自检：
-- SELECT table_name, column_name, column_type, column_default FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND column_name = 'print_form' ORDER BY table_name;

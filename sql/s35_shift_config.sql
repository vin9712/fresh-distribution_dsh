-- =============================================================================
-- s35：配送点班次配置（客户级开关 + 配送点班次模式 + 订单班次）
--
-- 背景：大长江总单纸面按「配送点×班次」出列（华铃食堂白班/夜班、大长江白班、
--       棠下白班）。班次此前被临时建模为独立配送点（名字里带白/夜班），
--       无法表达「班次同属于一个配送点」，也无法在下单时区分班次。
--       本脚本把班次提为一等配置：
--         · t_customer.shift_enabled  客户级开关（仅大长江=1，关闭时全链路忽略班次）
--         · t_customer_dept.shift_codes 该点支持的班次（biz_shift_type 字典值，逗号分隔）
--         · t_sale_order.shift_code    订单所选班次（下单时选，校验在点支持列表内）
--       矩阵列 = 配送点 × 班次；历史无班次单归入白班（DAY，业务确认）。
--
-- 幂等：information_schema 探测 + PREPARE，可重复执行。执行前请备份（sql/db_bak/）。
-- 生效：字典有缓存，执行后需在「系统管理→字典管理」刷新缓存或重新登录。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- 1. t_customer.shift_enabled ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_customer' AND COLUMN_NAME = 'shift_enabled'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_customer`
        ADD COLUMN `shift_enabled` tinyint(1) NOT NULL DEFAULT 0
            COMMENT ''是否启用班次（0否=配送点不分班次，1是=下单需选班次并按班次出列；仅大长江启用）'' AFTER `doc_merge_same_item`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. t_customer_dept.shift_codes ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_customer_dept' AND COLUMN_NAME = 'shift_codes'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_customer_dept`
        ADD COLUMN `shift_codes` varchar(32) NOT NULL DEFAULT ''''
            COMMENT ''该配送点支持的班次（biz_shift_type 字典值逗号分隔，如 DAY,NIGHT；空=不分班次）'' AFTER `mnemonic_code`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. t_sale_order.shift_code ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_sale_order' AND COLUMN_NAME = 'shift_code'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_sale_order`
        ADD COLUMN `shift_code` varchar(16) NOT NULL DEFAULT ''''
            COMMENT ''订单班次（客户启用班次时必填且须在该配送点支持列表内；空=不分班次）'' AFTER `customer_dept_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 4. 字典 biz_shift_type ----------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '配送点班次', 'biz_shift_type', '0', 'admin', sysdate(), 's35 配送点班次（白班/夜班）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'biz_shift_type');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '白班', 'DAY', 'biz_shift_type', '', 'primary', 'Y', '0', 'admin', sysdate(), 's35 白班'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'biz_shift_type' AND `dict_value` = 'DAY');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '夜班', 'NIGHT', 'biz_shift_type', '', 'warning', 'N', '0', 'admin', sysdate(), 's35 夜班'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'biz_shift_type' AND `dict_value` = 'NIGHT');

-- 5) 自检：执行后应返回 shift_enabled / shift_codes / shift_code 三列与两条字典
-- SELECT column_name FROM information_schema.COLUMNS
--  WHERE table_schema = DATABASE()
--    AND ((table_name='t_customer' AND column_name='shift_enabled')
--      OR (table_name='t_customer_dept' AND column_name='shift_codes')
--      OR (table_name='t_sale_order' AND column_name='shift_code'));
-- SELECT dict_sort, dict_label, dict_value FROM sys_dict_data WHERE dict_type='biz_shift_type' ORDER BY dict_sort;

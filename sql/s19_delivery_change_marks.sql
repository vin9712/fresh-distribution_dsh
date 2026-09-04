-- =============================================================================
-- s19：D-055 送货单视图化——订单明细变更标记 + 打印分界日志
--
-- 业务定稿（2026-09-03）：送货单不再是独立单证，而是订单的视图（数据源 t_sale_order_detail）。
--   配送前改动 = 正常更新订单（change_type=0，无标记）；
--   配送后（已打印）改动 = 原订单不变，变更以标记附加明细行：
--     加单 change_type=1（新行，应收 num + 实收 actual_num）
--     换货 change_type=2（换入行），被换行同时标 change_type=3，两行同 change_group
--     退货 change_type=3（原行应送/实收归 0）
--   打印分界 = 该 客户+日期+点 在 t_delivery_print_log 有记录即「已打印=配送后」。
--
-- 幂等：information_schema 探测 + PREPARE；建表用 CREATE TABLE IF NOT EXISTS。
-- 执行前请全库备份（sql/db_bak/）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- 1. t_sale_order_detail：变更标记三列 ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_sale_order_detail' AND COLUMN_NAME = 'change_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_sale_order_detail`
        ADD COLUMN `change_type`   tinyint NOT NULL DEFAULT 0
            COMMENT ''变更标记:0正常(含配送前更新) 1加单(配送后补充) 2换货 3退货（D-055）'' AFTER `sort`,
        ADD COLUMN `change_group`  bigint unsigned DEFAULT NULL
            COMMENT ''换货组号(被换行与换货行同组关联, 非换货为 NULL)'' AFTER `change_type`,
        ADD COLUMN `change_remark` varchar(200) DEFAULT NULL
            COMMENT ''变更说明(如:换货 原土豆→大白菜)'' AFTER `change_group`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. t_delivery_print_log：打印分界日志 ----------
CREATE TABLE IF NOT EXISTS `t_delivery_print_log` (
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`      bigint unsigned NOT NULL COMMENT '客户ID',
    `delivery_date`    date            NOT NULL COMMENT '配送日期',
    `customer_dept_id` bigint unsigned DEFAULT NULL COMMENT '配送点ID（点单打印；总单打印为 NULL）',
    `print_time`       datetime        NOT NULL COMMENT '打印时间',
    `print_by`         varchar(64)     DEFAULT '' COMMENT '打印人',
    `template_id`      bigint unsigned DEFAULT NULL COMMENT '模板ID',
    `create_by`        varchar(64)     DEFAULT '' COMMENT '创建者',
    `create_time`      datetime        DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_customer_date` (`customer_id`, `delivery_date`),
    KEY `idx_dept` (`customer_id`, `delivery_date`, `customer_dept_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='送货单打印日志（打印分界：已打印=配送后，变更需标记 D-055）';

-- 自检：
-- SELECT column_name, column_type, column_default FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 't_sale_order_detail' AND column_name LIKE 'change%';
-- SHOW CREATE TABLE t_delivery_print_log;

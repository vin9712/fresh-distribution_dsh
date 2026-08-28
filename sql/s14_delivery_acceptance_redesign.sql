-- ============================================================
-- S14：订单-送货-验收链路重构（三层模型地基）
-- 依据: docs/01-design/订单-送货-验收链路详细设计.md v1.2 §三（2026-08-28 定稿）
--
-- 内容:
--   1. 新表: t_delivery_batch / t_delivery_source_item / t_acceptance_revoke_log
--           t_return_order + t_return_item / t_job_run_log
--   2. 存量表: t_delivery_order 加批次/补单/作废维度; t_delivery_order_detail 去掉 order_id 唯一键(G5);
--             acceptance 加撤回字段; acceptance_item 更名 loss_quantity→difference_quantity + 加点级归属/差异原因类型;
--             t_customer 加组单策略两列
--   3. 字典: 4 个新字典 + 送货单状态补"已作废(3)"
--   4. 菜单/权限: 退货单菜单、送货单作废/补生成按钮、验收撤销按钮、验收单菜单恢复可见
--
-- 幂等: 全部可重复执行（CREATE IF NOT EXISTS / information_schema 探测 / WHERE NOT EXISTS）
-- 命名: 设计稿写 t_acceptance / t_acceptance_item，实际库表为 acceptance / acceptance_item
--       （无 t_ 前缀，s0_2_table_baseline.sql 建立），本脚本按实际表名执行。
-- 编号: TH(退货) 序列无需预插——BizCodeServiceImpl.nextSeq 首次使用时自动建行。
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 新表
-- ============================================================

-- ---------- 1.1 t_delivery_batch 客户每日配送批次（第一层，客户+日期唯一，策略快照） ----------
CREATE TABLE IF NOT EXISTS `t_delivery_batch` (
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT,
    `customer_id`     bigint unsigned NOT NULL COMMENT '客户ID',
    `delivery_date`   date NOT NULL COMMENT '配送日期',
    `scope_type`      varchar(32) NOT NULL DEFAULT 'DELIVERY_POINT_DATE' COMMENT '组单策略快照:CUSTOMER_DATE/DELIVERY_POINT_DATE',
    `merge_same_item` tinyint(1) NOT NULL DEFAULT 1 COMMENT '跨订单/跨点相同商品合并快照',
    `template_id`     bigint unsigned DEFAULT NULL COMMENT '模板绑定快照(打印层)',
    `layout_json`     varchar(500) DEFAULT NULL COMMENT '布局参数快照 column_count/rows_per_column',
    `status`          tinyint NOT NULL DEFAULT 0 COMMENT '0有效 1关闭(当日确认不再生成)',
    `version`         int unsigned NOT NULL DEFAULT 0,
    `is_deleted`      tinyint(1) NOT NULL DEFAULT 0,
    `create_by` varchar(64) DEFAULT '', `create_time` datetime,
    `update_by` varchar(64) DEFAULT '', `update_time` datetime,
    `remark`        varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `unq_customer_date` (`customer_id`, `delivery_date`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户每日配送批次(内部配货边界)';

-- ---------- 1.2 t_delivery_source_item 送货来源分配（P0 核心：订单行→送货行，应送台账） ----------
CREATE TABLE IF NOT EXISTS `t_delivery_source_item` (
    `id`                     bigint unsigned NOT NULL AUTO_INCREMENT,
    `delivery_id`            bigint unsigned NOT NULL COMMENT '送货单ID',
    `delivery_detail_id`     bigint unsigned NOT NULL COMMENT '聚合送货行ID',
    `sale_order_id`          bigint unsigned NOT NULL COMMENT '来源销售订单',
    `sale_order_detail_id`   bigint unsigned NOT NULL COMMENT '来源订单行',
    `customer_dept_id`       bigint unsigned NOT NULL COMMENT '来源配送点(追溯/差异归属)',
    `sku_id`                 bigint unsigned DEFAULT NULL,
    `product_name`           varchar(200) NOT NULL COMMENT '来源行品名快照',
    `allocated_quantity`     decimal(10,2) NOT NULL COMMENT '本订单行分配到该送货行的数量',
    `unit_price`             decimal(10,2) NOT NULL DEFAULT 0 COMMENT '来源行单价快照(成本归属)',
    `is_deleted`             tinyint(1) NOT NULL DEFAULT 0,
    `create_time`            datetime,
    `create_by`              varchar(64) DEFAULT '',
    PRIMARY KEY (`id`),
    -- 近期唯一：一张订单行只能进入一次有效送货（D-005）；作废软删后重建不撞键
    UNIQUE KEY `unq_sale_order_detail` (`sale_order_detail_id`, `is_deleted`),
    KEY `idx_delivery` (`delivery_id`),
    KEY `idx_delivery_detail` (`delivery_detail_id`),
    KEY `idx_sale_order` (`sale_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送货来源明细(订单行→送货行分配)';

-- ---------- 1.3 t_acceptance_revoke_log 验收撤回审计（Q16/D-014） ----------
CREATE TABLE IF NOT EXISTS `t_acceptance_revoke_log` (
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
    `acceptance_id` bigint unsigned NOT NULL,
    `snapshot_json` longtext NOT NULL COMMENT '撤回前主表+明细完整JSON',
    `reason`        varchar(200) NOT NULL,
    `revoked_by`    varchar(64) NOT NULL,
    `revoked_time`  datetime NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_acceptance` (`acceptance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验收撤回审计';

-- ---------- 1.4 t_return_order / t_return_item 退货单（D-032/D-034：独立于验收，不撤回历史验收） ----------
CREATE TABLE IF NOT EXISTS `t_return_order` (
    `id`                bigint unsigned NOT NULL AUTO_INCREMENT,
    `code`              varchar(32) NOT NULL COMMENT 'THyyyyMMddNNN',
    `customer_id`       bigint unsigned NOT NULL,
    `customer_dept_id`  bigint unsigned DEFAULT NULL,
    `delivery_id`       bigint unsigned NOT NULL COMMENT '原送货单',
    `acceptance_id`     bigint unsigned NOT NULL COMMENT '原验收单(退货单价来源)',
    `return_date`       date NOT NULL,
    `total_amount`      decimal(12,2) NOT NULL DEFAULT 0 COMMENT '合计退货金额(负项入对账)',
    `status`            tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已提交(质检中) 2质检完成 3已完成',
    `inspected_by`      varchar(64) DEFAULT NULL COMMENT '质检处理人',
    `inspected_time`    datetime DEFAULT NULL,
    `settle_scope`      tinyint NOT NULL DEFAULT 0 COMMENT '0结算前当期冲销 1结算后下期冲销(提交时计算快照)',
    `version`           int unsigned NOT NULL DEFAULT 0,
    `is_deleted`        tinyint(1) NOT NULL DEFAULT 0,
    `create_by`         varchar(64) DEFAULT '', `create_time` datetime,
    `update_by`         varchar(64) DEFAULT '', `update_time` datetime,
    `remark`            varchar(500),
    PRIMARY KEY (`id`),
    UNIQUE KEY `unq_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货单(独立于验收,不撤回历史验收)';

CREATE TABLE IF NOT EXISTS `t_return_item` (
    `id`                 bigint unsigned NOT NULL AUTO_INCREMENT,
    `return_id`          bigint unsigned NOT NULL,
    `acceptance_item_id` bigint unsigned NOT NULL COMMENT '来源验收明细行',
    `sku_id`             bigint unsigned DEFAULT NULL,
    `product_name`       varchar(200) NOT NULL,
    `product_spec`       varchar(200),
    `product_unit`       varchar(50),
    `return_quantity`    decimal(10,2) NOT NULL COMMENT '退货数量<=实收-已退',
    `unit_price`         decimal(10,2) NOT NULL COMMENT '=原验收单价(不可改)',
    `amount`             decimal(12,2) NOT NULL,
    `quality_result`     tinyint DEFAULT NULL COMMENT '质检:1可再售(入库) 2不可再售(报损)',
    `quality_note`       varchar(255),
    PRIMARY KEY (`id`),
    KEY `idx_return` (`return_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货明细(数量上限=实收-累计已退)';

-- ---------- 1.5 t_job_run_log 定时任务运行记录（Q36/D-037 工作台告警数据源） ----------
CREATE TABLE IF NOT EXISTS `t_job_run_log` (
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
    `job_name`      varchar(64) NOT NULL COMMENT 'DELIVERY_GENERATE 等',
    `biz_date`      date NOT NULL,
    `status`        tinyint NOT NULL COMMENT '0成功 1失败 2部分失败(遗漏订单)',
    `message`       varchar(500) DEFAULT NULL COMMENT '异常摘要/遗漏提示',
    `warning_count` int NOT NULL DEFAULT 0 COMMENT '遗漏订单数',
    `run_time`      datetime NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_job_date` (`job_name`,`biz_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务运行记录(工作台告警)';

-- ============================================================
-- 2. 存量表变更（幂等 ALTER：information_schema 探测 + PREPARE）
-- ============================================================

-- ---------- 2.1 t_delivery_order：批次/策略/补单/作废维度 ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_delivery_order' AND COLUMN_NAME = 'batch_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_delivery_order`
        ADD COLUMN `batch_id`       bigint unsigned DEFAULT NULL COMMENT ''所属配送批次'' AFTER `delivery_point_id`,
        ADD COLUMN `scope_type`     varchar(32)  DEFAULT ''DELIVERY_POINT_DATE'' COMMENT ''本单组单范围快照(总单时=CUSTOMER_DATE)'' AFTER `batch_id`,
        ADD COLUMN `doc_kind`       tinyint      NOT NULL DEFAULT 0 COMMENT ''0正常单 1补充单(遗漏订单单独成单)'' AFTER `scope_type`,
        ADD COLUMN `predecessor_id` bigint unsigned DEFAULT NULL COMMENT ''作废重建来源单ID'' AFTER `doc_kind`,
        ADD COLUMN `void_reason`    varchar(200) DEFAULT NULL COMMENT ''作废原因'',
        ADD COLUMN `void_by`        varchar(64)  DEFAULT NULL COMMENT ''作废人'',
        ADD COLUMN `void_time`      datetime     DEFAULT NULL COMMENT ''作废时间''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2.2 t_delivery_order_detail：去掉 order_id 唯一键（G5 结构性修复） ----------
-- order_id 列保留仅供历史行查询；s5_1 若已删除则本步自动跳过
SET @idx_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_delivery_order_detail'
      AND INDEX_NAME = 'unq_order_id'
);
SET @ddl := IF(@idx_exists > 0,
    'ALTER TABLE `t_delivery_order_detail` DROP INDEX `unq_order_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2.3 acceptance：撤回字段 ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance' AND COLUMN_NAME = 'revoke_reason'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `acceptance`
        ADD COLUMN `revoke_reason` varchar(200) DEFAULT NULL COMMENT ''撤回原因'',
        ADD COLUMN `revoked_by`    varchar(64)  DEFAULT NULL COMMENT ''撤回人'',
        ADD COLUMN `revoked_time`  datetime     DEFAULT NULL COMMENT ''撤回时间''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2.4 acceptance_item：更名 loss_quantity→difference_quantity + 点级归属/差异原因类型 ----------
-- 更名（仅当旧列存在且新列不存在）
SET @old_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance_item' AND COLUMN_NAME = 'loss_quantity'
);
SET @new_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance_item' AND COLUMN_NAME = 'difference_quantity'
);
SET @ddl := IF(@old_exists = 1 AND @new_exists = 0,
    'ALTER TABLE `acceptance_item`
        CHANGE COLUMN `loss_quantity` `difference_quantity` decimal(10,2) DEFAULT NULL COMMENT ''验收差异=实收-送货(正超收/负短收)''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 新列（一次探测一列）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance_item' AND COLUMN_NAME = 'customer_dept_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `acceptance_item`
        ADD COLUMN `customer_dept_id` bigint unsigned DEFAULT NULL COMMENT ''明细所属配送点(A类总单按点展开,B/C类也填)'' AFTER `delivery_item_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance_item' AND COLUMN_NAME = 'reason_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `acceptance_item`
        ADD COLUMN `reason_type` tinyint DEFAULT NULL COMMENT ''差异原因类型:1短收(acceptance_shortfall_reason) 2超收(acceptance_overage_reason)'' AFTER `loss_reason`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2.5 t_customer：组单策略（客户级，D-015 配送点不覆盖） ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_customer' AND COLUMN_NAME = 'doc_scope_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_customer`
        ADD COLUMN `doc_scope_type`      varchar(32) NOT NULL DEFAULT ''DELIVERY_POINT_DATE'' COMMENT ''组单策略:CUSTOMER_DATE/DELIVERY_POINT_DATE'',
        ADD COLUMN `doc_merge_same_item` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''相同商品合并成行''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. 字典
-- ============================================================

-- ---------- 3.1 delivery_no_print_reason 未打印送达原因（Q18/D-018） ----------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '送货未打印送达原因', 'delivery_no_print_reason', '0', 'admin', sysdate(), '送货单未打印直接送达时登记（免纸/电子单据/录单补登/设备故障/其他）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'delivery_no_print_reason');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '客户免纸', 'customer_paperless', 'delivery_no_print_reason', '', 'info', 'N', '0', 'admin', sysdate(), '客户无需纸质单据'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_no_print_reason' AND `dict_value` = 'customer_paperless');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '已发电子单据', 'electronic', 'delivery_no_print_reason', '', 'primary', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_no_print_reason' AND `dict_value` = 'electronic');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 3, '录单补登', 'rework', 'delivery_no_print_reason', '', 'warning', 'N', '0', 'admin', sysdate(), '后续回补打印/补录'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_no_print_reason' AND `dict_value` = 'rework');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 4, '设备故障', 'device_fault', 'delivery_no_print_reason', '', 'danger', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_no_print_reason' AND `dict_value` = 'device_fault');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 5, '其他', 'other', 'delivery_no_print_reason', '', 'info', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_no_print_reason' AND `dict_value` = 'other');

-- ---------- 3.2 acceptance_shortfall_reason 短收原因 ----------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '验收短收原因', 'acceptance_shortfall_reason', '0', 'admin', sysdate(), '实收<送货（差异原因类型1）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'acceptance_shortfall_reason');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '缺货', 'shortage', 'acceptance_shortfall_reason', '', 'warning', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_shortfall_reason' AND `dict_value` = 'shortage');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '拒收', 'refuse', 'acceptance_shortfall_reason', '', 'danger', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_shortfall_reason' AND `dict_value` = 'refuse');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 3, '损耗', 'spoilage', 'acceptance_shortfall_reason', '', 'warning', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_shortfall_reason' AND `dict_value` = 'spoilage');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 4, '质量问题', 'quality', 'acceptance_shortfall_reason', '', 'danger', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_shortfall_reason' AND `dict_value` = 'quality');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 5, '错送', 'wrong_send', 'acceptance_shortfall_reason', '', 'info', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_shortfall_reason' AND `dict_value` = 'wrong_send');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 6, '其他', 'other', 'acceptance_shortfall_reason', '', 'info', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_shortfall_reason' AND `dict_value` = 'other');

-- ---------- 3.3 acceptance_overage_reason 超收原因 ----------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '验收超收原因', 'acceptance_overage_reason', '0', 'admin', sysdate(), '实收>送货（差异原因类型2）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'acceptance_overage_reason');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '临时加送', 'temp_add', 'acceptance_overage_reason', '', 'primary', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_overage_reason' AND `dict_value` = 'temp_add');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '计量差异', 'scale_diff', 'acceptance_overage_reason', '', 'info', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_overage_reason' AND `dict_value` = 'scale_diff');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 3, '录单遗漏', 'order_miss', 'acceptance_overage_reason', '', 'warning', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_overage_reason' AND `dict_value` = 'order_miss');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 4, '其他', 'other', 'acceptance_overage_reason', '', 'info', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_overage_reason' AND `dict_value` = 'other');

-- ---------- 3.4 return_quality_result 退货质检结果 ----------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '退货质检结果', 'return_quality_result', '0', 'admin', sysdate(), '退货单质检：可再售入库/不可再售报损'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'return_quality_result');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '可再售(入库)', '1', 'return_quality_result', '', 'success', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'return_quality_result' AND `dict_value` = '1');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '不可再售(报损)', '2', 'return_quality_result', '', 'danger', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'return_quality_result' AND `dict_value` = '2');
-- ⚠️ 存量库迁移（s14 初版曾插入 reusable/damaged 字符串值，与 t_return_item.quality_result tinyint 1/2 口径不一致）
-- 由 sql/s14e_return_quality_dict_fix.sql 幂等迁移，此处不重复处理。

-- ---------- 3.5 送货单状态字典补"已作废(3)"（dict_type 107 = t_delivery_order_status） ----------
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 3, '已作废', '3', 't_delivery_order_status', '', 'danger', 'N', '0', 'admin', sysdate(), '送货单作废后状态，来源分配已释放'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 't_delivery_order_status' AND `dict_value` = '3');

-- ============================================================
-- 4. 菜单/权限（portable：不写死 menu_id，按 component 定位父菜单）
-- 说明：如需给非 admin 角色授权，请在"系统管理-角色管理"中勾选新菜单
-- ============================================================

-- ---------- 4.1 退货单菜单（挂在验收单同级 = 订单管理目录下） ----------
SET @order_parent := (SELECT parent_id FROM sys_menu WHERE component = 'order/acceptance/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单', @order_parent, 4, 'return', 'order/return/index', NULL, '', 1, 0, 'C', '0', '0', 'return:list', 'refund', 'admin', sysdate(), '退货单菜单(独立于验收单,不撤回历史验收)'
WHERE @order_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE component = 'order/return/index');

SET @return_menu := (SELECT menu_id FROM sys_menu WHERE component = 'order/return/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单查询', @return_menu, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'return:query', '#', 'admin', sysdate(), ''
WHERE @return_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @return_menu AND perms = 'return:query');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单新增', @return_menu, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'return:add', '#', 'admin', sysdate(), ''
WHERE @return_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @return_menu AND perms = 'return:add');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单修改', @return_menu, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'return:edit', '#', 'admin', sysdate(), ''
WHERE @return_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @return_menu AND perms = 'return:edit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单删除', @return_menu, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'return:remove', '#', 'admin', sysdate(), ''
WHERE @return_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @return_menu AND perms = 'return:remove');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单提交', @return_menu, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'return:submit', '#', 'admin', sysdate(), ''
WHERE @return_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @return_menu AND perms = 'return:submit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单质检', @return_menu, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'return:inspect', '#', 'admin', sysdate(), ''
WHERE @return_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @return_menu AND perms = 'return:inspect');

-- ---------- 4.2 送货单：作废 / 按客户补生成 按钮 ----------
SET @delivery_menu := (SELECT menu_id FROM sys_menu WHERE component = 'order/delivery/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '送货单作废', @delivery_menu, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:void', '#', 'admin', sysdate(), '作废未验收送货单并释放来源订单'
WHERE @delivery_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @delivery_menu AND perms = 'order:delivery:void');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '送货单补生成', @delivery_menu, 7, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:generateCustomer', '#', 'admin', sysdate(), '按客户补生成(遗漏订单补充单)'
WHERE @delivery_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @delivery_menu AND perms = 'order:delivery:generateCustomer');

-- ---------- 4.3 验收单：撤销按钮 + 菜单恢复可见（方案A 曾隐藏验收单入口） ----------
SET @acceptance_menu := (SELECT menu_id FROM sys_menu WHERE component = 'order/acceptance/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '验收单撤销', @acceptance_menu, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'acceptance:revoke', '#', 'admin', sysdate(), '撤回已提交验收单(留审计快照,订单回到已配送)'
WHERE @acceptance_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @acceptance_menu AND perms = 'acceptance:revoke');

UPDATE sys_menu SET visible = '0', status = '0'
WHERE component = 'order/acceptance/index' AND (visible <> '0' OR status <> '0');
UPDATE sys_menu SET visible = '0', status = '0'
WHERE menu_type = 'F' AND parent_id = @acceptance_menu AND (visible <> '0' OR status <> '0');

-- ============================================================
-- 5. 存量数据回填（可选，默认注释）
-- ============================================================
-- 设计口径（详细设计 §三 + 附录·迁移与灰度）：t_delivery_source_item 只为近期单据回填，
-- 更早单据标"历史无来源"（验收页来源对照列显示"—"）。
-- 下述 SQL 为「昨日待处理单据近似回填」模板：按 order_id 命中的送货明细行，
-- 以同订单同商品行的 (sale_order_detail_id) 近似对应（合并行无法精确拆分，接受近似）。
-- 执行前请人工核对行数；仅对仍有效的送货单（status<>3 且 is_deleted=0）执行。
--
-- INSERT INTO t_delivery_source_item
--     (delivery_id, delivery_detail_id, sale_order_id, sale_order_detail_id, customer_dept_id,
--      sku_id, product_name, allocated_quantity, unit_price, is_deleted, create_time, create_by)
-- SELECT dod.order_id,
--        dod.id,
--        sod.order_id,
--        sod.id,
--        d.delivery_point_id,
--        dod.sku_id,
--        dod.product_name,
--        dod.quantity,
--        dod.unit_price,
--        0,
--        NOW(),
--        's14-backfill'
-- FROM t_delivery_order_detail dod
-- JOIN t_delivery_order d           ON d.id = dod.order_id AND d.is_deleted = 0
-- JOIN t_sale_order_detail sod      ON sod.order_id = (SELECT so.id FROM t_sale_order so WHERE ...) -- 模板：来源订单行匹配键按实际可追溯性确定
-- WHERE dod.order_id IS NOT NULL
--   AND d.delivery_date >= CURDATE() - INTERVAL 1 DAY;
--
-- ⚠️ 旧明细行的 order_id 唯一键曾被 s5_1 调整为 (order_id, sku_id) 联合唯一，
--    勾选生成的行可精确回填（每行 order_id → sale_order_detail.id 需按 sku+名称匹配）；
--    合并生成(s5_1 改造后)的行无法精确回填来源订单行——该部分按"历史无来源"处理。
--    正式回填方案在 T3 统一生成服务上线前由人工评估，此处仅留模板占位。

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- S14 结束
-- ============================================================

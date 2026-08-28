-- =====================================================================
-- W0-4.4 打印模板管理与发布门禁：模板状态(草稿/测试/发布) + 版本历史 + 强制预览记录
-- 内容：1) t_print_template 加 status / test_watermark；2) 建 t_print_template_version；
--       3) 建 t_print_preview_log。
-- 幂等：information_schema 探测后 ALTER；CREATE TABLE IF NOT EXISTS；init_all.sql 已同步。
-- 回滚参考：DELETE 相应列/表：DROP TABLE t_print_preview_log, t_print_template_version；
--           ALTER TABLE t_print_template DROP COLUMN test_watermark, DROP COLUMN status;
-- =====================================================================

-- ---------- 1. t_print_template 加状态/测试水印列（幂等） ----------
SET @has_status := (SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_print_template' AND COLUMN_NAME = 'status');
SET @ddl_status := IF(@has_status = 0,
  'ALTER TABLE `t_print_template` ADD COLUMN `status` tinyint(3) NOT NULL DEFAULT 0 COMMENT ''模板状态：0草稿 1已测试 2已发布（W0-4.4）'' AFTER `is_default`',
  'SELECT 1');
PREPARE s1 FROM @ddl_status; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @has_wm := (SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_print_template' AND COLUMN_NAME = 'test_watermark');
SET @ddl_wm := IF(@has_wm = 0,
  'ALTER TABLE `t_print_template` ADD COLUMN `test_watermark` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''测试水印标记（测试打印整页水印，不计正式次数）'' AFTER `status`',
  'SELECT 1');
PREPARE s2 FROM @ddl_wm; EXECUTE s2; DEALLOCATE PREPARE s2;

-- ---------- 2. 版本快照表 ----------
CREATE TABLE IF NOT EXISTS `t_print_template_version` (
  `id`                bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id`       bigint(20)    NOT NULL COMMENT '模板ID',
  `version_no`        int(10)       NOT NULL DEFAULT 1 COMMENT '版本号（自增）',
  `name`              varchar(200)  NOT NULL COMMENT '模板名称快照',
  `content`           json          NOT NULL COMMENT '模板内容快照(JSON)',
  `bind_type`         tinyint(3)    NOT NULL DEFAULT 3 COMMENT '绑定类型快照',
  `customer_id`       bigint(20)    DEFAULT NULL COMMENT '客户ID快照',
  `delivery_point_id` bigint(20)    DEFAULT NULL COMMENT '绑定配送点ID快照',
  `copies`            int(10)       NOT NULL DEFAULT 1 COMMENT '联数快照',
  `published_by`      varchar(64)   DEFAULT '' COMMENT '发布人',
  `published_time`    datetime      DEFAULT NULL COMMENT '发布时间',
  `remark`            varchar(500)  DEFAULT NULL COMMENT '版本说明（回滚记录来源版本）',
  PRIMARY KEY (`id`),
  KEY `idx_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打印模板版本快照(W0-4.4)';

-- ---------- 3. 预览记录表 ----------
CREATE TABLE IF NOT EXISTS `t_print_preview_log` (
  `id`                bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id`       bigint(20)    NOT NULL COMMENT '模板ID',
  `delivery_order_id` bigint(20)    DEFAULT NULL COMMENT '送货单ID（汇总预览为空）',
  `operator`          varchar(64)   DEFAULT '' COMMENT '操作人',
  `preview_time`      datetime      DEFAULT NULL COMMENT '预览时间',
  PRIMARY KEY (`id`),
  KEY `idx_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打印预览记录(W0-4.4)';

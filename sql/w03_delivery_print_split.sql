-- ============================================================
-- W0-2.2 送货单打印拆分配置（「配置模型 + 版本表」）
-- 幂等脚本，可重复执行
--
-- 背景（蓝图「送货单打印拆分配置/送货调整版本/自动结构恢复」）：
--   为未打印送货单建立打印拆分/输出配置与版本记录：
--   - 默认按配送点一张；例外可合并配送点或按最大行数拆分；
--   - 跨配送点合单仅允许 A4 输出；针式仅面向单配送点、固定每页 10 条；
--   - 未打印单的排序/拆分/合并每次保存为可追溯打印配置版本；支持一键恢复自动生成结构；
--   - 配置变更必须审计（以版本记录承载）。
--
-- 建模：
--   1. t_delivery_print_config：一张送货单一份当前打印拆分/输出配置
--       （拆分方式 split_mode / 介质 media_type / 分页行数 rows_per_page /
--        打印结构 structure_json(第N/M张) / 是否自动结构 auto_generated）；
--   2. t_delivery_print_config_version：每次保存/恢复追加一份版本快照
--       （版本序号 version_no 自增 + 变更说明 + 操作人/时间）。
--
-- 回滚：
--   DROP TABLE IF EXISTS `t_delivery_print_config_version`;
--   DROP TABLE IF EXISTS `t_delivery_print_config`;
-- ============================================================

-- ---------- 1. t_delivery_print_config 送货单打印拆分配置 ----------
CREATE TABLE IF NOT EXISTS `t_delivery_print_config` (
    `id`                 bigint unsigned NOT NULL AUTO_INCREMENT,
    `delivery_order_id`  bigint unsigned NOT NULL COMMENT '送货单ID',
    `split_mode`         varchar(32)  NOT NULL DEFAULT 'DEFAULT_PER_DEPT' COMMENT '拆分方式:DEFAULT_PER_DEPT默认按配送点/CROSS_POINT_MERGE跨点合单(仅A4)/MAX_ROWS_SPLIT按最大行数拆分',
    `media_type`         varchar(32)  NOT NULL DEFAULT 'A4' COMMENT '输出介质:A4激光打印/DOT_MATRIX针式多联打印',
    `rows_per_page`      int          NOT NULL DEFAULT 10 COMMENT '分页行数(针式单点固定10;A4按页高自动分页记录期望值)',
    `structure_json`     longtext COMMENT '打印结构JSON(明细ID顺序+分页桶,含第N/M张)',
    `auto_generated`     tinyint(1)   NOT NULL DEFAULT 1 COMMENT '当前结构是否系统自动生成(0=手工调整)',
    `version`            int unsigned NOT NULL DEFAULT 0,
    `is_deleted`         tinyint(1)   NOT NULL DEFAULT 0,
    `create_by`          varchar(64)  DEFAULT '',
    `create_time`        datetime,
    `update_by`          varchar(64)  DEFAULT '',
    `update_time`        datetime,
    `remark`             varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `unq_delivery_order` (`delivery_order_id`, `is_deleted`),
    KEY `idx_delivery` (`delivery_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送货单打印拆分配置(未打印单排序/拆分/合并;配置变更承载于版本表)';

-- ---------- 2. t_delivery_print_config_version 打印配置版本记录 ----------
CREATE TABLE IF NOT EXISTS `t_delivery_print_config_version` (
    `id`                 bigint unsigned NOT NULL AUTO_INCREMENT,
    `delivery_order_id`  bigint unsigned NOT NULL COMMENT '送货单ID',
    `version_no`         int          NOT NULL DEFAULT 1 COMMENT '版本序号(同一送货单内自增,从1起)',
    `split_mode`         varchar(32)  NOT NULL DEFAULT 'DEFAULT_PER_DEPT' COMMENT '拆分方式快照',
    `media_type`         varchar(32)  NOT NULL DEFAULT 'A4' COMMENT '输出介质快照',
    `rows_per_page`      int          NOT NULL DEFAULT 10 COMMENT '分页行数快照',
    `structure_json`     longtext COMMENT '打印结构快照',
    `auto_generated`     tinyint(1)   NOT NULL DEFAULT 0 COMMENT '该版本是否系统自动生成结构(1=是)',
    `change_note`        varchar(200) DEFAULT NULL COMMENT '变更说明(恢复自动结构固定"恢复自动生成结构")',
    `create_by`          varchar(64)  DEFAULT '',
    `create_time`        datetime,
    PRIMARY KEY (`id`),
    KEY `idx_delivery_version` (`delivery_order_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送货单打印配置版本记录(未打印单每保存一次一份快照,审计与恢复)';

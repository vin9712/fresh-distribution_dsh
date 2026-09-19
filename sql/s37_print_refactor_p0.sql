-- =============================================================================
-- s37：打印模块重构 P0 —— 版本快照含「版式设计 JSON」（修 PR-A1/A2）
--
-- 背景（见 docs/01-design/打印模块重构设计.md §一）：
--   同一份模板存在两个事实来源：t_print_template 存业务绑定/状态/版本（content=报表ID），
--   jimu_report.json_str 存真正的版式。导致两个正确性缺陷：
--     PR-A1 版本快照只写 content（报表ID），回滚只回滚指针、版式没回滚；
--     PR-A2 导出把 content 当 JSON 解析，真实模板（数字报表ID）必然报错。
--
-- 本脚本给 t_print_template_version 增加三列并回填，让版本快照成为「自包含的版式快照」：
--   design_json  —— 版式设计 JSON 快照（jimu_report.json_str）：回滚/导出的真实载荷
--   report_id    —— 该版本物化出的报表ID（审计/复现）
--   dataset_spec —— 数据接线快照（契约版本/端点/参数；P2 起由物化器填充）
--
-- 幂等：information_schema 探测 + PREPARE；回填带 IS NULL 保护，可重复执行。
-- 前置：s0_3（jimu_report）、w09（t_print_template_version）。执行前请备份（sql/db_bak/）。
-- 关联：init_all.sql 尾部同步了同款 DDL。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- 1. t_print_template_version.design_json ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_print_template_version' AND COLUMN_NAME = 'design_json'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_print_template_version`
        ADD COLUMN `design_json` LONGTEXT NULL
            COMMENT ''版式设计JSON快照(jimu_report.json_str)：回滚/导出载荷(PR-D2)'' AFTER `content`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. t_print_template_version.report_id ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_print_template_version' AND COLUMN_NAME = 'report_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_print_template_version`
        ADD COLUMN `report_id` varchar(64) NULL
            COMMENT ''该版本物化出的 JimuReport 报表ID（审计/复现）'' AFTER `design_json`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. t_print_template_version.dataset_spec ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_print_template_version' AND COLUMN_NAME = 'dataset_spec'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_print_template_version`
        ADD COLUMN `dataset_spec` LONGTEXT NULL
            COMMENT ''数据接线快照(契约版本/端点/参数)：导出迁移自包含(PR-D3)'' AFTER `report_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 4. 回填：历史版本 content=报表ID → 取 jimu_report.json_str ----------
-- content 为 JSON 列：数字ID 用 JSON_UNQUOTE 还原成字符串再与 jimu_report.id 连接。
UPDATE `t_print_template_version` v
JOIN `jimu_report` r ON r.`id` = JSON_UNQUOTE(v.`content`)
SET v.`report_id`   = JSON_UNQUOTE(v.`content`),
    v.`design_json` = r.`json_str`
WHERE v.`design_json` IS NULL;

-- content 已是设计 JSON（导入/物化中间态）的历史版本：直接作为 design_json
UPDATE `t_print_template_version`
SET `design_json` = `content`
WHERE `design_json` IS NULL
  AND `content` IS NOT NULL
  AND LEFT(JSON_UNQUOTE(`content`), 1) = '{';

-- ---------- 5. 自检 ----------
-- SELECT COUNT(*) total,
--        SUM(design_json IS NOT NULL) with_design,
--        SUM(report_id IS NOT NULL)   with_report
--   FROM t_print_template_version;

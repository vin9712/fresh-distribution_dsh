-- =============================================================================
-- s39：静态样板对象退役（P1 打印模块重构清理）
--
-- 背景：`JimuSampleImporter` 曾把客户原始纸面样张导入为「静态样板报表」
--       （jimu_report.code LIKE 'sample\_%'，静态 json_data，仅作版式对照，永不参与真实打印），
--       并登记草稿模板（t_print_template.code LIKE 'SAMPLE\_%'）。
--       P1 已删除该导入器与静态样板源文件（归档见 docs/assets/print/archive/），
--       模板改由「骨架生成（P3）+ 设计器」产出。本脚本清理库里残留的静态样板对象。
--
-- 保留：酒店**动态**总单模板（HOTEL_*_MATRIX，已发布，读真实订单）与全局模板。
-- 方式：软删（t_print_template.is_deleted=1 / jimu_report.del_flag=1），可人工恢复。
-- 幂等：UPDATE 天然幂等。执行前请备份（sql/db_bak/）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- 1. 停用静态样板草稿模板 ----------
UPDATE `t_print_template`
SET `is_deleted` = 1,
    `remark` = CONCAT(IFNULL(`remark`, ''), ' [P1 静态样板退役]')
WHERE `is_deleted` = 0
  AND (`code` LIKE 'SAMPLE\_%' OR `name` LIKE '%（静态样板）%');

-- ---------- 2. 软删静态样板报表 ----------
UPDATE `jimu_report`
SET `del_flag` = 1,
    `update_time` = NOW()
WHERE `del_flag` = 0
  AND `code` LIKE 'sample\_%';

-- ---------- 3. 自检 ----------
-- 期望：无 is_deleted=0 的 SAMPLE_ 模板；无 del_flag=0 的 sample_ 报表
-- SELECT COUNT(*) AS active_sample_tpl FROM t_print_template
--  WHERE is_deleted=0 AND (code LIKE 'SAMPLE\_%' OR name LIKE '%（静态样板）%');
-- SELECT COUNT(*) AS active_sample_report FROM jimu_report
--  WHERE del_flag=0 AND code LIKE 'sample\_%';
-- 保留的动态模板仍在：
-- SELECT id, code, name, status FROM t_print_template
--  WHERE is_deleted=0 AND code LIKE 'HOTEL\_%' ORDER BY id;

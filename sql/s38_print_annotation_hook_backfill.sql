-- =============================================================================
-- s38：打印模板报表回执钩子补齐/升级（P2 物化附带修复）
--
-- 背景：打印回执（PT-3）依赖报表 js_str 注入 print-annotation.js：真实打印后回传
--       /print/receipt 登记 D-055 打印分界。实测：
--         · 4 个酒店动态总单模板（金鸿楼/金兴楼/丽宫/大长江）js_str 为 NULL
--           ——导入脚本 tests/import-hotel-print-samples.mjs 克隆时未带钩子，
--             导致打印这些模板不登记打印分界；
--         · 早期模板钩子版本为 v=10，需升到配置版本 v=11。
--
-- 范围：仅处理「被 t_print_template 引用的报表」（打印模板），不动其它积木报表。
-- 幂等：WHERE 条件保证重复执行不重复写。执行前请备份（sql/db_bak/）。
-- 关联：JimuReportMaterializer#ensureAnnotationHook（应用侧同一逻辑，可经「重接」触发）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- 1. 补齐缺失钩子（js_str 为空或无 print-annotation.js） ----------
UPDATE `jimu_report` r
JOIN `t_print_template` t ON JSON_UNQUOTE(t.`content`) = r.`id`
SET r.`js_str` = '(function(){var s=document.createElement("script");s.src="/jmreport/desreport_/ext/print-annotation.js?v=11";document.head.appendChild(s);})()',
    r.`update_time` = NOW()
WHERE r.`del_flag` = 0
  AND t.`is_deleted` = 0
  AND (r.`js_str` IS NULL OR r.`js_str` = '' OR r.`js_str` NOT LIKE '%print-annotation.js%');

-- ---------- 2. 升级旧钩子版本 v=10 → v=11 ----------
UPDATE `jimu_report` r
JOIN `t_print_template` t ON JSON_UNQUOTE(t.`content`) = r.`id`
SET r.`js_str` = REPLACE(r.`js_str`, 'print-annotation.js?v=10', 'print-annotation.js?v=11'),
    r.`update_time` = NOW()
WHERE r.`del_flag` = 0
  AND t.`is_deleted` = 0
  AND r.`js_str` LIKE '%print-annotation.js?v=10%';

-- ---------- 3. 自检 ----------
-- SELECT t.id, t.name, r.id AS report_id, (r.js_str LIKE '%print-annotation.js%') AS has_hook
--   FROM t_print_template t JOIN jimu_report r ON r.id = JSON_UNQUOTE(t.content)
--  WHERE t.is_deleted = 0 ORDER BY t.id;

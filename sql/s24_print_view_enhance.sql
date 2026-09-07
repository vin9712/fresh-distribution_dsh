-- =============================================================================
-- s24：打印报表 view 页增强（Word 式标注：自定义文本框 + 单元格改值 + 查询栏关闭）
--
-- 背景：打印点单/总单跳转 /jmreport/view/{id}（JimuReport 2.0.0）。view 页原生
--       支持模板级增强：jimu_report.js_str 会被 eval("(jsStr)") 立即执行、
--       css_str 注入 <head>（见官方 view.js handleReportQueryInfo）。
--       打印参数均由 URL 透传（s22 已声明 search_flag=0），view 页查询栏无实际作用。
--
-- 内容：
--   1) 增强脚本本体：lin-entry/src/main/resources/static/jmreport/desreport_/ext/print-annotation.js
--      （随后端 jar 发布；改脚本需重新打包部署，并递增本文件引导串里的 ?v= 破缓存）
--   2) 为所有在用打印模板（t_print_template.content → jimu_report.id）注入 js_str 引导串：
--      异步加载增强脚本 + 初始化工具栏。用子查询覆盖，后续新增模板重跑本脚本即可。
--   3) 关闭两个种子模板（FLAT 点单 2099000000000000001 / MATRIX 总单 2599000000000000001）
--      的查询栏：json_str 里 querySetting.izOpenQueryBar → false。
--      其余模板如需关闭，在「打印模板管理 → 设计」里操作即可。
--
-- 幂等：全部 UPDATE，可重复执行。种子脚本 s6_2/s18 已同步此状态（新库重建无需重跑本脚本），
-- 但 s6_2 为 INSERT IGNORE、s18 为 REPLACE INTO，覆盖场景下仍建议重跑本脚本兑底。
-- 前置：s0_3（jimu_* 表）→ s6_2 → s18 → s22；后端需已包含 ext/print-annotation.js 重新打包。
-- 回滚：UPDATE `jimu_report` SET `js_str`=NULL WHERE `js_str` LIKE '%print-annotation.js%';
--       （查询栏如需恢复：将下方 REPLACE 的 true/false 对调重跑）
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- 1. 注入 js_str 引导串（覆盖所有在用打印模板） ----------
-- 注意：t_print_template.content 为 JSON 类型列，与 jimu_report.id(varchar) 关联须 JSON_UNQUOTE。
-- 引导串只做一件事：按序加载 ext/print-annotation.js?v=1（脚本自身含幂等保护与页面判定）。
-- 串内只允许双引号（SQL 单引号字面量）；?v= 与脚本版本同步递增。
UPDATE `jimu_report` jr
INNER JOIN (
    SELECT DISTINCT JSON_UNQUOTE(`content`) AS rid
    FROM `t_print_template`
    WHERE `render_engine` = 'jimureport'
      AND `is_deleted` = 0
      AND `content` IS NOT NULL AND JSON_UNQUOTE(`content`) <> ''
) tt ON tt.rid = jr.`id`
SET jr.`js_str` = '(function(){var s=document.createElement("script");s.src="/jmreport/desreport_/ext/print-annotation.js?v=10";document.head.appendChild(s);})()',
    jr.`update_by` = 'system',
    jr.`update_time` = NOW();

-- ---------- 2. 关闭种子模板查询栏（json_str 固定串替换） ----------
-- json_str 为 TEXT 长串，MySQL 5.7 无 JSON_SET 于 TEXT 的便捷路径，采用固定子串 REPLACE。
-- 两个种子模板的 querySetting 均为 {"izOpenQueryBar":true,"izDefaultQuery":true}（s6_2/s18 原样）。
UPDATE `jimu_report`
SET `json_str` = REPLACE(`json_str`,
    '"querySetting":{"izOpenQueryBar":true,"izDefaultQuery":true}',
    '"querySetting":{"izOpenQueryBar":false,"izDefaultQuery":true}'),
    `update_by` = 'system',
    `update_time` = NOW()
WHERE `id` IN ('2099000000000000001', '2599000000000000001')
  AND `json_str` LIKE '%"izOpenQueryBar":true%';
-- ⚠ izDefaultQuery 必须保持 true：false 语义是「打开不自动取数、等查询栏触发」，
--   而查询栏已关，将导致打印预览永远空白（踩过坑：2026-09-04）。

-- ---------- 3. 自检（执行后手工核对） ----------
-- 1) 引导串已注入（期望行数 = t_print_template 在用模板数，至少 2）：
-- SELECT jr.id, jr.name, LEFT(jr.js_str, 60) FROM `jimu_report` jr
--  WHERE jr.js_str LIKE '%print-annotation.js%';
-- 2) 查询栏已关（期望两个模板 izOpenQueryBar 均为 false）：
-- SELECT id, name, json_str LIKE '%"izOpenQueryBar":false%' AS qbar_off
--  FROM `jimu_report` WHERE id IN ('2099000000000000001','2599000000000000001');
-- 3) 打开 /jmreport/view/{id}?ticket=... 右侧应出现「标注」竖排页签；
--    浏览器控制台不应有 404（ext/print-annotation.js 随 jar 已发布）。

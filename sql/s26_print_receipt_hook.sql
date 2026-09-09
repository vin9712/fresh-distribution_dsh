-- =============================================================================
-- s26：打印回执钩子（PT-3，《客户日报表打印优化设计》§3.3 + §四）
--
-- 背景：打印分界登记时机从「前端开窗即登记」改为「报表页真实打印动作后回执登记」：
--       print-annotation.js 新增 notifyPrinted()——真实打印后写 localStorage['print_receipt']
--       （供批量打印抽屉监听推进）并 POST /print/receipt（票据自证，SecurityConfig 放行）。
--       前端 view.vue 同步删除开窗即登记的 markDeliveryPrinted 调用。
--
-- 本脚本只做 JimuReport 配置更新（不动业务表结构）：
--   1) 在用模板 js_str 引导版本 v=10 → v=11（破浏览器缓存，加载新钩子脚本）；
--   2) 总单长表模板 2599000000000000005（s25 入库时 js_str 为 NULL）补引导脚本；
--   3) 自检注释。
--
-- 幂等：REPLACE 不存在时 REPLACE 为 0 行、REPLACE 无命中为安全空操作；可重复执行。
-- 前置：s0_3（jimu_* 表）、s6_2/s18/s24/s25（在用模板）。
-- 执行前请全库备份（sql/db_bak/）。⚠ 未执行本脚本的环境：报表页无回执钩子，
--   点单/总单打印后「已打印」不会翻转（失败方向安全），执行后即恢复。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- 1. 在用模板 js_str 引导版本 v=10 → v=11 ----------
UPDATE `jimu_report`
SET `js_str` = REPLACE(`js_str`, 'print-annotation.js?v=10', 'print-annotation.js?v=11')
WHERE `js_str` LIKE '%print-annotation.js%';

-- ---------- 2. 长表模板（s25，js_str 原为 NULL）补引导 ----------
UPDATE `jimu_report`
SET `js_str` = '(function(){var s=document.createElement("script");s.src="/jmreport/desreport_/ext/print-annotation.js?v=11";document.head.appendChild(s);})()'
WHERE `id` = '2599000000000000005'
  AND (`js_str` IS NULL OR `js_str` = '' OR `js_str` NOT LIKE '%print-annotation.js%');

-- ---------- 3. 自检（执行后手工核对） ----------
-- 1) 在用模板均已挂 v=11 引导：
-- SELECT id, code, LEFT(js_str, 120) FROM jimu_report WHERE js_str LIKE '%print-annotation.js%';
-- 期望：2599000000000000001（总单宽表）/ 2599000000000000005（总单长表）/ 2099000000000000001（点单 FLAT）
--       及其他在用模板均含 print-annotation.js?v=11；
-- 2) 回执链路冒烟：浏览器打开报表视图 → 工具栏「打印」→ network 应看到 POST /print/receipt 200
--    且 {registered:true}（bizKey 主体）→ 系统「打印状态」接口翻转为已打印。

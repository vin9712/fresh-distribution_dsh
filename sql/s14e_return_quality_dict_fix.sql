-- ============================================================
-- s14e: return_quality_result 字典口径修正（2026-08-28）
--
-- 背景（T7 第二轮遗留债务）：
--   s14 初版将退货质检字典值插为字符串 reusable/damaged，
--   而 t_return_item.quality_result 为 tinyint（1=可再售 2=不可再售），
--   后端存整数、字典为字符串，前端无法字典驱动渲染（T7 暂用本地映射）。
--   本脚本将字典值统一为 '1'/'2'（dict_value 为 varchar，存 '1'/'2' 即可
--   与 DictTag 宽松匹配；写入后端仍为整数，无需任何 Java/表结构变更）。
--
-- 执行顺序：对已跑过 s14 的存量库，直接执行本脚本；
--   全新库走 init_all.sql / s14（已同步改为直接插入 '1'/'2'，无需本脚本）。
-- 幂等性：可重复执行；重复执行零变化。
-- 生效：字典有缓存，执行后需在「系统管理→字典管理」刷新缓存或重新登录。
-- ============================================================

-- 1) 老值迁移：reusable → '1'（仅当标签匹配且新值未占用，防重复键语义混乱）
UPDATE `sys_dict_data`
SET `dict_value` = '1'
WHERE `dict_type` = 'return_quality_result'
  AND `dict_label` = '可再售(入库)'
  AND `dict_value` <> '1';

-- 2) 老值迁移：damaged → '2'
UPDATE `sys_dict_data`
SET `dict_value` = '2'
WHERE `dict_type` = 'return_quality_result'
  AND `dict_label` = '不可再售(报损)'
  AND `dict_value` <> '2';

-- 3) 兑底补齐（老库若 s14 未跑或字典被误删，则补插入；幂等）
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '可再售(入库)', '1', 'return_quality_result', '', 'success', 'N', '0', 'admin', sysdate(), 's14e 兑底补齐'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'return_quality_result' AND `dict_value` = '1');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '不可再售(报损)', '2', 'return_quality_result', '', 'danger', 'N', '0', 'admin', sysdate(), 's14e 兑底补齐'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'return_quality_result' AND `dict_value` = '2');

-- 4) 自检：执行后应返回 2 行且 dict_value 均为 '1'/'2'，无 reusable/damaged 残留
-- SELECT dict_sort, dict_label, dict_value FROM sys_dict_data
--  WHERE dict_type = 'return_quality_result' ORDER BY dict_sort;

-- =============================================================================
-- s25：总单长表模板（横向动态列，通用版）入库
--
-- 背景：打印模板通用化改造（docs/01-design/打印模板通用数据结构与模板设计.md §4.5）。
--       总单改用「全交叉长表」数据源 + JimuReport 横向动态列分组（#{dc.groupRight(deptLabel)} /
--       #{dc.dynamic(num)}），配送点数量/顺序变化时模板零改动。宽表模板（s18）保留兼容套打。
--
-- 内容：
--   1) jimu_report   2599000000000000005  总单长表模板（landscape A4，横向动态列）
--   2) jimu_report_db 2599000000000000006/007/008  数据集 hm(head)/hc(columns)/dc(rows-long)
--      —— 端点 /print/deliveryMatrixData?...&rowsType=long；适配器：
--         hm → deliveryDataConvertAdapter（head 优先）
--         hc → deliveryMatrixColumnsConvertAdapter（columns 优先，s25 新增）
--         dc → deliveryRowsConvertAdapter（rows 优先）
--   3) jimu_report_db_param  ticket/customerId/deliveryDate/colBlock/rowsType（search_flag=0 不渲染查询控件）
--   4) t_print_template id=13  总单长表模板（全局默认 MATRIX，status=2 已发布）
--      ⚠ 与 id=12（宽表全局默认）并存：本行 is_default='1'，会把总单打印默认切到长表模板；
--        如需回退宽表，执行：UPDATE t_print_template SET is_default='1' WHERE id=12;
--        UPDATE t_print_template SET is_default='0' WHERE id=13;
--
-- 前置：s0_3（jimu_* 表）、s16（print_form 列）、后端已包含 deliveryMatrixColumnsConvertAdapter
--       且 /print/deliveryMatrixData 支持 rowsType=long（随本次代码发布）。
-- 幂等：REPLACE INTO / INSERT..ON DUP / UPDATE，可重复执行。
-- 执行前请全库备份（sql/db_bak/）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- 1. JimuReport 报表定义（总单长表 · 横向动态列） ----------
REPLACE INTO `jimu_report` (`id`, `code`, `name`, `note`, `status`, `type`, `json_str`, `api_url`, `thumb`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `api_method`, `api_code`, `template`, `view_count`, `css_str`, `js_str`, `py_str`, `tenant_id`, `update_count`, `submit_form`, `is_multi_sheet`) VALUES ('2599000000000000005', 'delivery_matrix_long_001', '总单长表模板（横向动态列）', '全交叉长表数据源 + 横向动态列分组，配送点增减模板零改动（通用打印设计 §4.5）', NULL, '0', '{\"loopBlockList\":[],\"querySetting\":{\"izOpenQueryBar\":false,\"izDefaultQuery\":true},\"recordSubTableOrCollection\":{\"group\":[],\"record\":[],\"range\":[]},\"printConfig\":{\"paper\":\"A4\",\"width\":210,\"height\":297,\"definition\":1,\"isBackend\":false,\"marginX\":8,\"marginY\":10,\"layout\":\"landscape\"},\"hidden\":{\"rows\":[],\"cols\":[]},\"queryFormSetting\":{\"useQueryForm\":false,\"dbKey\":\"\",\"idField\":\"\"},\"dbexps\":[],\"dicts\":[],\"freeze\":\"B4\",\"dataRectWidth\":1000,\"isViewContentHorizontalCenter\":false,\"autofilter\":{},\"validations\":[],\"cols\":{\"0\":{\"width\":45},\"1\":{\"width\":160},\"2\":{\"width\":70},\"3\":{\"width\":50},\"4\":{\"width\":90},\"len\":30},\"area\":{\"sri\":0,\"sci\":4,\"eri\":0,\"eci\":4,\"width\":100,\"height\":25},\"pyGroupEngine\":false,\"submitHandlers\":[],\"hiddenCells\":[],\"zonedEditionList\":[],\"rows\":{\"0\":{\"cells\":{\"0\":{\"merge\":[0,4],\"text\":\"${hm.printTitle}\",\"style\":1}},\"height\":34},\"1\":{\"cells\":{\"0\":{\"merge\":[0,1],\"text\":\"配送日期：${hm.deliveryDate}\"},\"2\":{\"text\":\"列块：${hm.colBlockLabel}\"},\"3\":{\"merge\":[0,1],\"text\":\"合计：${hm.totalQuantity}（${hm.totalKinds} 项 / ${hm.pointCount} 点）\"}},\"height\":22},\"2\":{\"cells\":{\"0\":{\"text\":\"序号\",\"style\":1},\"1\":{\"text\":\"菜品\",\"style\":1},\"2\":{\"text\":\"规格\",\"style\":1},\"3\":{\"text\":\"单位\",\"style\":1},\"4\":{\"text\":\"#{dc.groupRight(deptLabel)}\",\"style\":1,\"aggregate\":\"group\",\"direction\":\"right\"}},\"height\":24},\"3\":{\"cells\":{\"0\":{\"text\":\"#{dc.group(seq)}\"},\"1\":{\"text\":\"#{dc.group(rowKey)}\"},\"2\":{\"text\":\"#{dc.group(productSpec)}\"},\"3\":{\"text\":\"#{dc.group(productUnit)}\"},\"4\":{\"text\":\"#{dc.dynamic(num)}\",\"style\":1,\"aggregate\":\"dynamic\"}},\"height\":22},\"4\":{\"cells\":{\"0\":{\"merge\":[0,4],\"text\":\"合计：${hm.totalQuantity}（${hm.totalKinds} 项 / ${hm.pointCount} 点）\"}},\"height\":24},\"len\":100},\"rpbar\":{\"show\":true,\"pageSize\":\"\",\"btnList\":[]},\"fixedPrintHeadRows\":[2,3],\"fixedPrintTailRows\":[],\"displayConfig\":{},\"background\":false,\"name\":\"sheet1\",\"styles\":[{\"align\":\"center\"},{\"bgcolor\":\"#d9e1f2\",\"align\":\"center\"}],\"freezeLineColor\":\"rgb(185, 185, 185)\",\"merges\":[],\"excel_config_id\":\"2599000000000000005\"}', NULL, NULL, 'system', NOW(), 'system', NOW(), 0, NULL, NULL, 0, 0, NULL, NULL, NULL, '1', 0, NULL, NULL);

-- ---------- 2. 数据集（hm 单值表头 / hc 列定义 / dc 全交叉长表） ----------
REPLACE INTO `jimu_report_db` (`id`, `jimu_report_id`, `create_by`, `update_by`, `create_time`, `update_time`, `db_code`, `db_ch_name`, `db_type`, `db_table_name`, `db_dyn_sql`, `db_key`, `tb_db_key`, `tb_db_table_name`, `java_type`, `java_value`, `api_url`, `api_method`, `is_list`, `is_page`, `db_source`, `db_source_type`, `json_data`, `api_convert`, `iz_shared_source`, `jimu_shared_source_id`) VALUES
('2599000000000000006', '2599000000000000005', 'system', NULL, NOW(), NULL, 'hm', '总单表头', '1', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'http://localhost:8090/print/deliveryMatrixData?customerId=${customerId}&deliveryDate=${deliveryDate}&colBlock=${colBlock}&rowsType=long&ticket=${ticket}', '0', '0', '0', NULL, NULL, NULL, 'deliveryDataConvertAdapter', NULL, NULL),
('2599000000000000007', '2599000000000000005', 'system', NULL, NOW(), NULL, 'hc', '总单列定义（横向分组表头）', '1', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'http://localhost:8090/print/deliveryMatrixData?customerId=${customerId}&deliveryDate=${deliveryDate}&colBlock=${colBlock}&rowsType=long&ticket=${ticket}', '0', '1', '0', NULL, NULL, NULL, 'deliveryMatrixColumnsConvertAdapter', NULL, NULL),
('2599000000000000008', '2599000000000000005', 'system', NULL, NOW(), NULL, 'dc', '总单明细（全交叉长表）', '1', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'http://localhost:8090/print/deliveryMatrixData?customerId=${customerId}&deliveryDate=${deliveryDate}&colBlock=${colBlock}&rowsType=long&ticket=${ticket}', '0', '1', '0', NULL, NULL, NULL, 'deliveryRowsConvertAdapter', NULL, NULL);

-- ---------- 3. 数据集参数声明（URL 同名透传，不在报表页渲染查询控件） ----------
INSERT INTO `jimu_report_db_param`
    (`id`, `jimu_report_head_id`, `param_name`, `param_txt`, `param_value`, `order_num`,
     `create_by`, `create_time`, `search_flag`, `dict_code`, `ext_json`)
VALUES
    ('2599000000000000061', '2599000000000000006', 'customerId',   '客户ID',   '', 1, 'system', NOW(), 0, '', ''),
    ('2599000000000000062', '2599000000000000006', 'deliveryDate', '配送日期', '', 2, 'system', NOW(), 0, '', ''),
    ('2599000000000000063', '2599000000000000006', 'colBlock',     '列块序号', '1', 3, 'system', NOW(), 0, '', ''),
    ('2599000000000000064', '2599000000000000006', 'rowsType',     '行形态',   'long', 4, 'system', NOW(), 0, '', ''),
    ('2599000000000000065', '2599000000000000006', 'ticket',       '打印票据', '', 5, 'system', NOW(), 0, '', '')
ON DUPLICATE KEY UPDATE `param_value` = VALUES(`param_value`), `order_num` = VALUES(`order_num`);

INSERT INTO `jimu_report_db_param`
    (`id`, `jimu_report_head_id`, `param_name`, `param_txt`, `param_value`, `order_num`,
     `create_by`, `create_time`, `search_flag`, `dict_code`, `ext_json`)
VALUES
    ('2599000000000000071', '2599000000000000007', 'customerId',   '客户ID',   '', 1, 'system', NOW(), 0, '', ''),
    ('2599000000000000072', '2599000000000000007', 'deliveryDate', '配送日期', '', 2, 'system', NOW(), 0, '', ''),
    ('2599000000000000073', '2599000000000000007', 'colBlock',     '列块序号', '1', 3, 'system', NOW(), 0, '', ''),
    ('2599000000000000074', '2599000000000000007', 'rowsType',     '行形态',   'long', 4, 'system', NOW(), 0, '', ''),
    ('2599000000000000075', '2599000000000000007', 'ticket',       '打印票据', '', 5, 'system', NOW(), 0, '', '')
ON DUPLICATE KEY UPDATE `param_value` = VALUES(`param_value`), `order_num` = VALUES(`order_num`);

INSERT INTO `jimu_report_db_param`
    (`id`, `jimu_report_head_id`, `param_name`, `param_txt`, `param_value`, `order_num`,
     `create_by`, `create_time`, `search_flag`, `dict_code`, `ext_json`)
VALUES
    ('2599000000000000081', '2599000000000000008', 'customerId',   '客户ID',   '', 1, 'system', NOW(), 0, '', ''),
    ('2599000000000000082', '2599000000000000008', 'deliveryDate', '配送日期', '', 2, 'system', NOW(), 0, '', ''),
    ('2599000000000000083', '2599000000000000008', 'colBlock',     '列块序号', '1', 3, 'system', NOW(), 0, '', ''),
    ('2599000000000000084', '2599000000000000008', 'rowsType',     '行形态',   'long', 4, 'system', NOW(), 0, '', ''),
    ('2599000000000000085', '2599000000000000008', 'ticket',       '打印票据', '', 5, 'system', NOW(), 0, '', '')
ON DUPLICATE KEY UPDATE `param_value` = VALUES(`param_value`), `order_num` = VALUES(`order_num`);

-- ---------- 4. 打印模板登记（全局默认 · MATRIX · 已发布；总单默认切到长表） ----------
REPLACE INTO `t_print_template` (`id`, `render_engine`, `bind_type`, `print_form`, `customer_id`, `delivery_point_id`, `copies`, `is_default`, `status`, `test_watermark`, `code`, `name`, `content`, `data`, `type`, `is_deleted`, `version`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (13, 'jimureport', 3, 'MATRIX', 0, NULL, 1, '1', 2, 0, 'TPL-DELIVERY-MATRIX-LONG', '总单长表模板（横向动态列·全局默认）', '2599000000000000005', '{}', 0, 0, 0, 'system', NOW(), '', NULL, '通用打印设计 §4.5：全交叉长表+横向动态列，配送点增减模板零改动');
UPDATE `t_print_template` SET `is_default` = '0' WHERE `id` = 12 AND `is_default` = '1';

-- ---------- 5. 自检（执行后手工核对） ----------
-- 1) 模板/数据集/参数就位：
-- SELECT id, code, name FROM `jimu_report` WHERE id = '2599000000000000005';
-- SELECT id, db_code, api_convert, is_list FROM `jimu_report_db` WHERE jimu_report_id = '2599000000000000005';
-- 2) 默认模板已切换（期望 12→0，13→1）：
-- SELECT id, code, is_default, status FROM `t_print_template` WHERE id IN (12, 13);
-- 3) 数据接口长表冒烟（带票据浏览器访问或 curl）：
--    /print/deliveryMatrixData?customerId=10&deliveryDate=yyyy-MM-dd&rowsType=long&ticket=ptk_xxx
--    期望 rows 条数 = 菜品行数 × 配送点数，首行含全部分配点 deptSeq 1..N。

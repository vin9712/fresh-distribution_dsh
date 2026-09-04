-- ============================================================
-- S6-2 打印落地：JimuReport 送货单模板 + API 数据集 + 参数 + 全局默认绑定模板
-- 全部 INSERT IGNORE，可重复执行
-- ============================================================

-- 0) 打印模板类型字典
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (110, '打印模板类型', 't_print_template_type', '0', 'admin', sysdate(), '', NULL, '打印模板类型');

INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(50, 0, '送货单', '0', 't_print_template_type', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, '打印模板类型-送货单'),
(51, 1, '汇总表', '1', 't_print_template_type', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '打印模板类型-汇总表');

-- 1) JimuReport 送货单打印模板（jimu_report）
--    content 约定：t_print_template.content = jimu_report.id
INSERT IGNORE INTO `jimu_report`
(`id`, `code`, `name`, `note`, `status`, `type`, `json_str`, `api_url`, `thumb`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `api_method`, `api_code`, `template`, `view_count`, `css_str`, `js_str`, `py_str`, `tenant_id`, `update_count`, `submit_form`, `is_multi_sheet`)
VALUES
('2099000000000000001', 'delivery_print_default_001', '送货单打印模板（默认）', '生鲜配送送货单默认打印模板', NULL, '0',
'{"loopBlockList":[],"querySetting":{"izOpenQueryBar":true,"izDefaultQuery":true},"recordSubTableOrCollection":{"group":[],"record":[],"range":[]},"printConfig":{"paper":"A4","width":210,"height":297,"definition":1,"isBackend":false,"marginX":10,"marginY":10,"layout":"portrait"},"hidden":{"rows":[],"cols":[]},"queryFormSetting":{"useQueryForm":true,"dbKey":"","idField":""},"dbexps":[],"dicts":[],"freeze":"A1","dataRectWidth":470,"isViewContentHorizontalCenter":false,"autofilter":{},"validations":[],"cols":{"0":{"width":36},"1":{"width":150},"2":{"width":44},"3":{"width":60},"4":{"width":60},"5":{"width":60},"6":{"width":60},"len":20},"area":{"sri":0,"sci":5,"eri":0,"eci":5,"width":100,"height":25},"pyGroupEngine":false,"submitHandlers":[],"excel_config_id":"2099000000000000001","hiddenCells":[],"zonedEditionList":[],"rows":{"0":{"cells":{"0":{"merge":[0,5],"text":"送货单","style":1},"1":{},"2":{},"3":{},"4":{},"5":{}},"height":36},"1":{"cells":{"0":{"text":"客户：${hd.deliveryPointName}","merge":[0,2]},"1":{},"2":{},"3":{"text":"单号：${hd.code}","merge":[0,1]},"4":{},"5":{"text":"日期：${hd.deliveryDate}","merge":[0,2]},"6":{},"7":{}}},"2":{"cells":{"0":{"text":"序号","style":1},"1":{"text":"商品名称","style":1},"2":{"text":"单位","style":1},"3":{"text":"计划数量","style":1},"4":{"text":"实收数量","style":1},"5":{"text":"单价","style":1},"6":{"text":"金额","style":1}},"height":26},"3":{"cells":{"0":{"text":"#{dd.seq}"},"1":{"text":"#{dd.productName}"},"2":{"text":"#{dd.productUnit}"},"3":{"text":"#{dd.num}"},"4":{"text":"#{dd.acceptanceNum}"},"5":{"text":"#{dd.price}"},"6":{"text":"#{dd.amount}"}},"height":24},"4":{"cells":{"0":{},"1":{},"2":{},"3":{},"4":{},"5":{"text":"合计"},"6":{"text":"${hd.totalAmount}"}},"height":24},"5":{"height":30,"cells":{"0":{"text":"收货单位（签章）：","merge":[0,3]},"1":{},"2":{},"3":{},"4":{"text":"送货单位：","merge":[0,2]},"5":{},"6":{}}},"len":100},"rpbar":{"show":true,"pageSize":"","btnList":[]},"fixedPrintHeadRows":[],"fixedPrintTailRows":[],"displayConfig":{},"background":false,"name":"sheet1","styles":[{"align":"center"},{"bgcolor":"#d9e1f2","align":"center"}],"freezeLineColor":"rgb(185, 185, 185)","merges":[]}',
NULL, NULL, 'admin', sysdate(), 'admin', sysdate(), 0, NULL, NULL, 0, 0, NULL, NULL, NULL, '1', 0, 0, NULL);

-- 2) 数据集：表头（单行）hd + 明细（列表）dd；api_convert=deliveryDataConvertAdapter 做格式适配
INSERT IGNORE INTO `jimu_report_db`
(`id`, `jimu_report_id`, `create_by`, `update_by`, `create_time`, `update_time`, `db_code`, `db_ch_name`, `db_type`, `db_table_name`, `db_dyn_sql`, `db_key`, `tb_db_key`, `tb_db_table_name`, `java_type`, `java_value`, `api_url`, `api_method`, `is_list`, `is_page`, `db_source`, `db_source_type`, `json_data`, `api_convert`, `iz_shared_source`, `jimu_shared_source_id`)
VALUES
('2099000000000000002', '2099000000000000001', 'admin', 'admin', sysdate(), sysdate(), 'hd', '送货单表头', '1', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'http://localhost:8090/print/deliveryHead?deliveryOrderId=${deliveryOrderId}&ticket=${ticket}', '0', '0', '0', '', NULL, NULL, 'deliveryDataConvertAdapter', NULL, NULL),
('2099000000000000003', '2099000000000000001', 'admin', 'admin', sysdate(), sysdate(), 'dd', '送货单明细', '1', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'http://localhost:8090/print/deliveryData?deliveryOrderId=${deliveryOrderId}&ticket=${ticket}', '0', '1', '0', '', NULL, NULL, 'deliveryDataConvertAdapter', NULL, NULL);

-- 2b) 已存在行更新为转换器 + 票据透传配置（幂等，W0-4.1：数据集 URL 透传短时一次性打印票据）
UPDATE `jimu_report_db`
SET `api_convert` = 'deliveryDataConvertAdapter',
    `api_url` = CASE WHEN `db_code` = 'hd'
                     THEN 'http://localhost:8090/print/deliveryHead?deliveryOrderId=${deliveryOrderId}&ticket=${ticket}'
                     ELSE 'http://localhost:8090/print/deliveryData?deliveryOrderId=${deliveryOrderId}&ticket=${ticket}' END
WHERE `id` IN ('2099000000000000002', '2099000000000000003');

-- 3) 数据集参数：deliveryOrderId（明细数据集进查询表单，表头数据集同参数自动带入）
INSERT IGNORE INTO `jimu_report_db_param`
(`id`, `jimu_report_head_id`, `param_name`, `param_txt`, `param_value`, `order_num`, `create_by`, `create_time`, `update_by`, `update_time`, `search_flag`, `widget_type`, `search_mode`, `dict_code`, `search_format`, `ext_json`)
VALUES
('2099000000000000004', '2099000000000000002', 'deliveryOrderId', '送货单ID', '', 1, 'admin', sysdate(), NULL, NULL, 0, NULL, NULL, '', NULL, ''),
('2099000000000000005', '2099000000000000003', 'deliveryOrderId', '送货单ID', '', 1, 'admin', sysdate(), NULL, NULL, 1, NULL, NULL, '', NULL, '');

-- 5) 打印视图默认自动查询（URL 传参 deliveryOrderId 后打开即加载数据；幂等 REPLACE）
UPDATE `jimu_report`
SET `json_str` = REPLACE(`json_str`, '"izDefaultQuery":false', '"izDefaultQuery":true')
WHERE `id` = '2099000000000000001';

-- 6) 全局默认绑定模板（t_print_template）
--    content = jimu_report.id；bind_type=3 + is_default='1'；联数 copies=1
INSERT IGNORE INTO `t_print_template`
(`customer_id`, `code`, `name`, `content`, `data`, `type`, `render_engine`, `bind_type`, `delivery_point_id`, `copies`, `is_default`, `is_deleted`, `version`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES
(0, 'TMP-DELIVERY-DEFAULT', '送货单打印模板（全局默认）', '2099000000000000001', '{}', 0, 'jimureport', 3, NULL, 1, '1', 0, 0, 'admin', sysdate(), '', NULL, 'S6-2 预置全局默认送货单模板');

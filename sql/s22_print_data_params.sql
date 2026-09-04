-- =============================================================================
-- s22：打印数据接口取数主体扩展（D-055 送货单视图化收尾）
--
-- 背景：D-055 后送货单不再是独立单证，总单/点单打印主体从「送货单ID」改为
--       「客户+配送日期(+配送点)」，实时取订单明细。/print/deliveryMatrixData、
--       /print/deliveryData、/print/deliveryHead 已支持双主体（deliveryOrderId 优先，
--       否则按 customerId/customerDeptId/deliveryDate），票据绑定同步支持 bizKey。
--
-- 本脚本只做两件事（均为 JimuReport 配置数据，不动业务表结构）：
--   1) 更新 4 个 API 数据集的 api_url，补齐新主体参数与 ticket 透传参数；
--   2) 为这些数据集补声明 jimu_report_db_param（报表页不显示输入框：search_flag=0）。
--
-- 兼容性：deliveryOrderId 参数保留在 URL 首位，历史送货单打印链路不受影响；
--         未替换的 ${param} 字面量与空串在后端统一按「未传」容错解析。
--
-- 幂等：UPDATE 天然幂等；参数声明用 INSERT ... ON DUPLICATE KEY UPDATE。
-- 前置：需先导入 s0_3_jimureport_init.sql（jimu_* 表）、s6_2_print_seed.sql（FLAT 点单默认模板）、
--       s18_universal_print_templates.sql（MATRIX 总单模板），否则本脚本的 UPDATE 命中 0 行、
--       参数声明会成为孤儿行（不报错但无意义）。
-- 验证：已在本地 MySQL 5.7 空库按 init_all.sql → s0_3 → s6_2 → s18 → s22 全链路重建跑通（双跑幂等）。
-- 执行前请全库备份：mysqldump --single-transaction（见 sql/db_bak/）
-- =============================================================================

-- ---------- 1. 数据集 URL ----------

-- 点单（FLAT·全局默认模板 2099000000000000001）表头
UPDATE `jimu_report_db`
SET `api_url` = 'http://localhost:8090/print/deliveryHead?deliveryOrderId=${deliveryOrderId}&customerId=${customerId}&customerDeptId=${customerDeptId}&deliveryDate=${deliveryDate}&ticket=${ticket}',
    `update_time` = NOW()
WHERE `id` = '2099000000000000002';

-- 点单（FLAT）明细
UPDATE `jimu_report_db`
SET `api_url` = 'http://localhost:8090/print/deliveryData?deliveryOrderId=${deliveryOrderId}&customerId=${customerId}&customerDeptId=${customerDeptId}&deliveryDate=${deliveryDate}&ticket=${ticket}',
    `update_time` = NOW()
WHERE `id` = '2099000000000000003';

-- 总单（MATRIX·全局默认模板 2599000000000000001）表头
UPDATE `jimu_report_db`
SET `api_url` = 'http://localhost:8090/print/deliveryMatrixData?deliveryOrderId=${deliveryOrderId}&customerId=${customerId}&deliveryDate=${deliveryDate}&colBlock=${colBlock}&ticket=${ticket}',
    `update_time` = NOW()
WHERE `id` = '2599000000000000002';

-- 总单（MATRIX）明细（矩阵行）
UPDATE `jimu_report_db`
SET `api_url` = 'http://localhost:8090/print/deliveryMatrixData?deliveryOrderId=${deliveryOrderId}&customerId=${customerId}&deliveryDate=${deliveryDate}&colBlock=${colBlock}&ticket=${ticket}',
    `update_time` = NOW()
WHERE `id` = '2599000000000000003';

-- ---------- 2. 数据集参数声明 ----------
-- 说明：param_value 留空 = 由报表视图 URL 同名参数带入；search_flag=0 = 不在报表页渲染查询控件。

INSERT INTO `jimu_report_db_param`
    (`id`, `jimu_report_head_id`, `param_name`, `param_txt`, `param_value`, `order_num`,
     `create_by`, `create_time`, `search_flag`, `dict_code`, `ext_json`)
VALUES
    -- 点单表头（2099000000000000002）：deliveryOrderId 已存在（id ...004），补其余
    ('2099000000000000012', '2099000000000000002', 'customerId',      '客户ID',    '', 2, 'system', NOW(), 0, '', ''),
    ('2099000000000000013', '2099000000000000002', 'customerDeptId',  '配送点ID',  '', 3, 'system', NOW(), 0, '', ''),
    ('2099000000000000014', '2099000000000000002', 'deliveryDate',    '配送日期',  '', 4, 'system', NOW(), 0, '', ''),
    ('2099000000000000015', '2099000000000000002', 'ticket',          '打印票据',  '', 5, 'system', NOW(), 0, '', ''),
    -- 点单明细（2099000000000000003）：deliveryOrderId 已存在（id ...005），补其余
    ('2099000000000000022', '2099000000000000003', 'customerId',      '客户ID',    '', 2, 'system', NOW(), 0, '', ''),
    ('2099000000000000023', '2099000000000000003', 'customerDeptId',  '配送点ID',  '', 3, 'system', NOW(), 0, '', ''),
    ('2099000000000000024', '2099000000000000003', 'deliveryDate',    '配送日期',  '', 4, 'system', NOW(), 0, '', ''),
    ('2099000000000000025', '2099000000000000003', 'ticket',          '打印票据',  '', 5, 'system', NOW(), 0, '', ''),
    -- 总单表头（2599000000000000002）
    ('2599000000000000011', '2599000000000000002', 'deliveryOrderId', '送货单ID',  '', 1, 'system', NOW(), 0, '', ''),
    ('2599000000000000012', '2599000000000000002', 'customerId',      '客户ID',    '', 2, 'system', NOW(), 0, '', ''),
    ('2599000000000000013', '2599000000000000002', 'deliveryDate',    '配送日期',  '', 3, 'system', NOW(), 0, '', ''),
    ('2599000000000000014', '2599000000000000002', 'colBlock',        '列块序号',  '', 4, 'system', NOW(), 0, '', ''),
    ('2599000000000000015', '2599000000000000002', 'ticket',          '打印票据',  '', 5, 'system', NOW(), 0, '', ''),
    -- 总单明细（2599000000000000003）
    ('2599000000000000021', '2599000000000000003', 'deliveryOrderId', '送货单ID',  '', 1, 'system', NOW(), 0, '', ''),
    ('2599000000000000022', '2599000000000000003', 'customerId',      '客户ID',    '', 2, 'system', NOW(), 0, '', ''),
    ('2599000000000000023', '2599000000000000003', 'deliveryDate',    '配送日期',  '', 3, 'system', NOW(), 0, '', ''),
    ('2599000000000000024', '2599000000000000003', 'colBlock',        '列块序号',  '', 4, 'system', NOW(), 0, '', ''),
    ('2599000000000000025', '2599000000000000003', 'ticket',          '打印票据',  '', 5, 'system', NOW(), 0, '', '')
ON DUPLICATE KEY UPDATE
    `jimu_report_head_id` = VALUES(`jimu_report_head_id`),
    `param_name` = VALUES(`param_name`),
    `param_txt`  = VALUES(`param_txt`),
    `order_num`  = VALUES(`order_num`),
    `search_flag`= VALUES(`search_flag`),
    `update_time`= NOW();

-- ---------- 2.5 统一数据集请求方法 = 0（GET） ----------
-- jimu_report_db.api_method 语义是 '0'=GET / '1'=POST（建表注释），全库 90 个既有数据集均为 '0'。
-- s18 曾误把 4 个打印数据集写成 'GET' 字符串，JimuReport 无法识别 → 落默认 POST，
-- 打在 GET-only 的 /print/* 端点上必 405（报表页整页无数据）。统一回 '0'。
UPDATE `jimu_report_db`
SET `api_method` = '0'
WHERE `id` IN ('2099000000000000002','2099000000000000003','2599000000000000002','2599000000000000003')
  AND `api_method` <> '0';

-- ---------- 2.6 MATRIX 数据集补转换适配器与分页关闭 ----------
-- /print/deliveryMatrixData 返回 {head, columns, rows} 业务对象——head 与 rows 同响应，
-- 两个数据集需配不同适配器拆开（lin-entry 注册的 ApiDataConvertAdapter）：
--   hm 表头 → deliveryDataConvertAdapter（head 优先，包成单行）
--   dm 明细 → deliveryRowsConvertAdapter（取 rows 列表；head 优先会拿错，明细渲染为空）
-- s18 当年漏配 api_convert → 报表页整页无数据。is_page 对齐 FLAT 的 '0'（不分页，由列块分页）。
UPDATE `jimu_report_db`
SET `api_convert` = 'deliveryDataConvertAdapter',
    `is_page` = '0'
WHERE `id` = '2599000000000000002';
UPDATE `jimu_report_db`
SET `api_convert` = 'deliveryRowsConvertAdapter',
    `is_page` = '0'
WHERE `id` = '2599000000000000003';

-- ---------- 3. 发布全局默认模板（打印解析只认 status=2 已发布） ----------
-- 2026-09-02 真机验证时发现：预置的全局默认模板停在「已测试/未发布」态，
-- selectBindTemplate 只取 status=2 → 打印无模板可用。此处幂等发布两支全局默认（点单 FLAT + 总单 MATRIX）。
UPDATE `t_print_template`
SET `status`      = 2,
    `update_by`   = 'system',
    `update_time` = NOW()
WHERE `bind_type` = 3
  AND `is_deleted` = 0
  AND `status` <> 2;

-- ---------- 4. 自检（执行后手工核对，期望 4 行 api_url 均含 ticket 与新主体参数） ----------
-- SELECT id, db_ch_name, api_url FROM `jimu_report_db`
--  WHERE id IN ('2099000000000000002','2099000000000000003','2599000000000000002','2599000000000000003');
-- SELECT jimu_report_head_id, GROUP_CONCAT(param_name ORDER BY order_num) params
--   FROM `jimu_report_db_param`
--  WHERE jimu_report_head_id IN ('2099000000000000002','2099000000000000003','2599000000000000002','2599000000000000003')
--  GROUP BY jimu_report_head_id;
-- SELECT id, name, print_form, status FROM `t_print_template` WHERE bind_type = 3 AND is_deleted = 0;

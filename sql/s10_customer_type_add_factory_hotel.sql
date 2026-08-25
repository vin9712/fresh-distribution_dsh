-- s10_customer_type_add_factory_hotel.sql
-- 客户类型字典 t_customer_type 补充「工厂」「酒店」（2026 业务确认）
-- 背景：实际业务中工厂客户通常约 3 个配送点，酒店客户通常约 5 个配送点（可能含员工食堂，
--       菜品与价格独立），此为经验值而非硬规则，仅用于归类与统计。
-- 幂等：已存在同 dict_value 时跳过。执行后需重新登录刷新字典缓存。

INSERT INTO sys_dict_data
(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark)
SELECT 5, '工厂', '5', 't_customer_type', '', 'danger', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 't_customer_type' AND dict_value = '5');

INSERT INTO sys_dict_data
(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark)
SELECT 6, '酒店', '6', 't_customer_type', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 't_customer_type' AND dict_value = '6');

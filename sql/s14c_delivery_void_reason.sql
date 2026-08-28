-- ============================================================================
-- s14c: 送货单作废原因字典（S14/T4 作废与补单配套）
-- 依据: docs/01-design/订单-送货-验收链路详细设计.md §5.2 voidDeliveryOrder(reasonCode, reasonNote)
--       §八 order/delivery/index.vue「作废（弹原因下拉）」
-- 说明: s14 已建 void 按钮权限(order:delivery:void)，本文件仅补作废原因字典，
--       供前端作废弹窗下拉使用；void_reason 落库格式 =「标签[：补充说明]」（标签由后端解析）。
-- 幂等: 全部 WHERE NOT EXISTS，可重复执行。
-- ============================================================================

-- ---------- 1. 字典类型 delivery_void_reason（送货单作废原因） ----------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '送货单作废原因', 'delivery_void_reason', '0', 'admin', sysdate(), '送货单手工作废时登记（录单错误/重复生成/客户取消/缺货取消/其他；选其他必须填补充说明）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'delivery_void_reason');

-- ---------- 2. 字典数据 ----------
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '录单错误', 'order_error', 'delivery_void_reason', '', 'warning', 'N', '0', 'admin', sysdate(), '订单录错（商品/数量/价格/配送点等）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_void_reason' AND `dict_value` = 'order_error');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '重复生成', 'duplicate', 'delivery_void_reason', '', 'info', 'N', '0', 'admin', sysdate(), '同一批订单重复出单，作废多余单据'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_void_reason' AND `dict_value` = 'duplicate');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 3, '客户取消', 'customer_cancel', 'delivery_void_reason', '', 'info', 'N', '0', 'admin', sysdate(), '客户取消当日订单/配送点'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_void_reason' AND `dict_value` = 'customer_cancel');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 4, '缺货取消', 'out_of_stock', 'delivery_void_reason', '', 'danger', 'N', '0', 'admin', sysdate(), '商品缺货整单取消（部分缺货请改单而非作废）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_void_reason' AND `dict_value` = 'out_of_stock');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 5, '其他', 'other', 'delivery_void_reason', '', 'info', 'N', '0', 'admin', sysdate(), '其他原因，必须填写补充说明'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_void_reason' AND `dict_value` = 'other');

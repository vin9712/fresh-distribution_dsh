-- ============================================================
-- S28 变更回退留痕（OA，《订单页一键验收链路设计》§4.4 回退）：
-- markReturned 将 num/actual_num 清零（应送0口径），原应收数量转入本列留痕，
-- 供「回退」恢复（change_type 还原 + num/actual_num 取快照）。
-- ============================================================

ALTER TABLE t_sale_order_detail
    ADD COLUMN change_original_num DECIMAL(12,2) NULL COMMENT '配送后变更回退用：标记退货/换货前的原应收数量' AFTER change_remark;

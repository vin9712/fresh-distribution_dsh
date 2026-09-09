-- ============================================================
-- S27 订单维度验收（OA，《订单页一键验收链路设计》）：
-- 验收单维度新增「一订单一验」，交互迁移到订单明细页（mode=acceptance）。
-- 既有 客户日/历史送货单 维度验收单不受影响（sale_order_id 保持 NULL）。
-- ============================================================

-- 订单维度验收：来源销售订单ID（唯一键仅约束非 NULL，MySQL 允许多行 NULL，历史单天然豁免）
-- 类型对齐主键 bigint unsigned（t_sale_order.id 同型）
ALTER TABLE acceptance
    ADD COLUMN sale_order_id BIGINT UNSIGNED NULL COMMENT '订单维度验收：来源销售订单ID（一订单一验，OA；历史/客户日单为NULL）' AFTER delivery_order_id;

ALTER TABLE acceptance
    ADD UNIQUE KEY uk_acceptance_sale_order (sale_order_id);

-- ============================================================
-- S5-1 送货生成：表结构适配（按商品合并 + 配送点维度）
-- ============================================================

-- 送货单增加配送点维度
ALTER TABLE `t_delivery_order`
  ADD COLUMN `delivery_point_id` bigint(20) DEFAULT NULL COMMENT '配送点ID（t_customer_dept.id）' AFTER `customer_id`;

-- 送货明细：移除"一单一订单"唯一约束，增加商品合并行字段
ALTER TABLE `t_delivery_order_detail`
  DROP INDEX `unq_order_id`,
  ADD COLUMN `sku_id` bigint(20) DEFAULT NULL COMMENT 'SKU（临时商品可空）' AFTER `order_code`,
  ADD COLUMN `product_name` varchar(200) DEFAULT NULL COMMENT '商品名称快照' AFTER `sku_id`,
  ADD COLUMN `product_unit` varchar(50) DEFAULT NULL COMMENT '单位快照' AFTER `product_name`,
  ADD COLUMN `product_spec` varchar(200) DEFAULT NULL COMMENT '规格快照' AFTER `product_unit`,
  ADD COLUMN `num` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '送货数量' AFTER `product_spec`,
  ADD COLUMN `price` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '单价快照' AFTER `num`,
  ADD COLUMN `amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '小计（num*price）' AFTER `price`;

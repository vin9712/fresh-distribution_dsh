-- ============================================================
-- S2-2 报价模板-客户绑定表（一个客户最多绑定一个模板）
-- ============================================================
CREATE TABLE IF NOT EXISTS `price_template_customer` (
  `id`          bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id` bigint(20)  NOT NULL COMMENT '报价模板ID',
  `customer_id` bigint(20)  NOT NULL COMMENT '客户ID',
  `create_by`   varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime    DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_id` (`customer_id`),
  KEY `idx_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价模板-客户绑定表';

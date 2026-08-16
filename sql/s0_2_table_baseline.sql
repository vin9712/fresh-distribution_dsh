-- ============================================================
-- S0-2 表结构基线：新建表 + 复用表改造字段
-- 依赖：无（可从空库执行，或对现有库执行增量）
-- 设计依据：DESIGN.md §6、DEVELOPMENT.md §2
-- ============================================================

-- ------------------------------------------------------------
-- 1. 基础数据新增：临时商品、全局别名、客户 SKU 映射
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `temp_product` (
  `id`            bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`          varchar(200) NOT NULL COMMENT '临时商品名称',
  `spec`          varchar(200) DEFAULT NULL COMMENT '规格',
  `unit`          varchar(50)  DEFAULT NULL COMMENT '单位',
  `default_price` decimal(10,2) DEFAULT NULL COMMENT '默认单价',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     DEFAULT NULL COMMENT '更新时间',
  `remark`        varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='临时商品表';

CREATE TABLE IF NOT EXISTS `product_alias` (
  `id`         bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `alias_type` tinyint(3)   NOT NULL DEFAULT 1 COMMENT '别名类型：1名称 2拼音 3英文缩写',
  `alias`      varchar(200) NOT NULL COMMENT '别名内容',
  `sku_id`     bigint(20)   NOT NULL COMMENT '关联SKU',
  `create_by`  varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time` datetime    DEFAULT NULL COMMENT '创建时间',
  `update_by`  varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime    DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alias` (`alias`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品全局别名表';

CREATE TABLE IF NOT EXISTS `customer_sku_mapping` (
  `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `customer_id`    bigint(20)   NOT NULL COMMENT '客户ID',
  `customer_alias` varchar(200) NOT NULL COMMENT '客户侧叫法/编码',
  `sku_id`         bigint(20)   NOT NULL COMMENT '我方SKU',
  `create_by`      varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`    datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`      varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`    datetime     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_alias` (`customer_id`, `customer_alias`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户SKU映射表（客户叫法->我方SKU）';

-- ------------------------------------------------------------
-- 2. 报价新增：报价模板、模板SKU价、配送点报价
--    客户报价复用 t_product_sku_quote（有效期字段已存在，无需加列）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `price_template` (
  `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`           varchar(200) NOT NULL COMMENT '模板名称',
  `status`         char(1)      NOT NULL DEFAULT '0' COMMENT '状态（0启用 1停用）',
  `effective_date` date         DEFAULT NULL COMMENT '生效日期',
  `expire_date`    date         DEFAULT NULL COMMENT '失效日期',
  `is_default`     char(1)      NOT NULL DEFAULT '0' COMMENT '是否全局默认（0否 1是）',
  `create_by`      varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`    datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`      varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`    datetime     DEFAULT NULL COMMENT '更新时间',
  `remark`         varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价模板表';

CREATE TABLE IF NOT EXISTS `price_template_sku` (
  `id`             bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id`    bigint(20)    NOT NULL COMMENT '模板ID',
  `sku_id`         bigint(20)    NOT NULL COMMENT 'SKU',
  `unit_price`     decimal(10,2) NOT NULL DEFAULT 0 COMMENT '单价',
  `effective_date` date          DEFAULT NULL COMMENT '生效日期',
  `expire_date`    date          DEFAULT NULL COMMENT '失效日期',
  `create_by`      varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`    datetime      DEFAULT NULL COMMENT '创建时间',
  `update_by`      varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`    datetime      DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_template_id` (`template_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价模板SKU价格表';

CREATE TABLE IF NOT EXISTS `delivery_point_price` (
  `id`                bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `delivery_point_id` bigint(20)    NOT NULL COMMENT '配送点ID（t_customer_dept.id）',
  `sku_id`            bigint(20)    NOT NULL COMMENT 'SKU',
  `unit_price`        decimal(10,2) NOT NULL DEFAULT 0 COMMENT '单价',
  `effective_date`    date          DEFAULT NULL COMMENT '生效日期',
  `expire_date`       date          DEFAULT NULL COMMENT '失效日期',
  `create_by`         varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`       datetime      DEFAULT NULL COMMENT '创建时间',
  `update_by`         varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`       datetime      DEFAULT NULL COMMENT '更新时间',
  `remark`            varchar(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_point_id` (`delivery_point_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送点报价表';

-- ------------------------------------------------------------
-- 3. 订单：调整记录表 + 复用表改造字段（t_sale_order 加 adjust_flag）
-- ------------------------------------------------------------
ALTER TABLE `t_sale_order`
  ADD COLUMN `adjust_flag` char(1) NOT NULL DEFAULT '0' COMMENT '是否发生配送后调整（0否 1是）' AFTER `status`;

CREATE TABLE IF NOT EXISTS `order_adjustment` (
  `id`            bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`      bigint(20)   NOT NULL COMMENT '原订单ID',
  `order_item_id` bigint(20)   DEFAULT NULL COMMENT '原订单行ID（可空）',
  `type`          tinyint(3)   NOT NULL COMMENT '类型：1加单 2退单 3换货',
  `reason`        varchar(500) DEFAULT NULL COMMENT '原因',
  `adjust_date`   date         NOT NULL COMMENT '调整日期（归属D天）',
  `detail_json`   text         COMMENT '明细说明JSON（换货含加/退两行）',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单加退换调整表';

-- ------------------------------------------------------------
-- 4. 采购：采购单主表与明细
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `purchase_order` (
  `id`               bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`             varchar(32)   NOT NULL COMMENT '采购单号（PCyyyyMMddNNN）',
  `order_date`       date          NOT NULL COMMENT '采购归属日期（=订单配送日期）',
  `source_type`      tinyint(3)    NOT NULL COMMENT '来源类型：1自动生成 2手工创建',
  `source_order_ids` varchar(2000) DEFAULT NULL COMMENT '来源订单ID列表（JSON）',
  `supplier_id`      bigint(20)    DEFAULT NULL COMMENT '供应商ID（可空，确认时后补）',
  `supplier_name`    varchar(200)  DEFAULT NULL COMMENT '供应商名称（直填）',
  `total_amount`     decimal(12,2) NOT NULL DEFAULT 0 COMMENT '采购总额',
  `status`           tinyint(3)    NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1已确认 2已入库',
  `create_by`        varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`      datetime      DEFAULT NULL COMMENT '创建时间',
  `update_by`        varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`      datetime      DEFAULT NULL COMMENT '更新时间',
  `remark`           varchar(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_order_date` (`order_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单主表';

CREATE TABLE IF NOT EXISTS `purchase_item` (
  `id`           bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `purchase_id`  bigint(20)    NOT NULL COMMENT '采购单ID',
  `sku_id`       bigint(20)    DEFAULT NULL COMMENT 'SKU（临时商品可空）',
  `product_name` varchar(200)  NOT NULL COMMENT '商品名称快照',
  `product_spec` varchar(200)  DEFAULT NULL COMMENT '规格快照',
  `product_unit` varchar(50)   DEFAULT NULL COMMENT '单位快照',
  `quantity`     decimal(10,2) NOT NULL DEFAULT 0 COMMENT '数量',
  `unit_price`   decimal(10,2) NOT NULL DEFAULT 0 COMMENT '采购单价（成本）',
  `subtotal`     decimal(12,2) NOT NULL DEFAULT 0 COMMENT '小计',
  `sort`         int(10)       NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_purchase_id` (`purchase_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单明细表';

-- ------------------------------------------------------------
-- 5. 验收：验收单主表与明细（一单一验）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `acceptance` (
  `id`                bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`              varchar(32)   NOT NULL COMMENT '验收单号（YSyyyyMMddNNN）',
  `delivery_order_id` bigint(20)    NOT NULL COMMENT '送货单ID（唯一，一单一验）',
  `customer_id`       bigint(20)    NOT NULL COMMENT '客户ID',
  `delivery_point_id` bigint(20)    DEFAULT NULL COMMENT '配送点ID',
  `accept_date`       date          NOT NULL COMMENT '验收日期',
  `total_amount`      decimal(12,2) NOT NULL DEFAULT 0 COMMENT '验收总额（结算依据）',
  `status`            tinyint(3)    NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1已提交',
  `create_by`         varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`       datetime      DEFAULT NULL COMMENT '创建时间',
  `update_by`         varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`       datetime      DEFAULT NULL COMMENT '更新时间',
  `remark`            varchar(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  UNIQUE KEY `uk_delivery_order_id` (`delivery_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验收单主表';

CREATE TABLE IF NOT EXISTS `acceptance_item` (
  `id`                 bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `acceptance_id`      bigint(20)    NOT NULL COMMENT '验收单ID',
  `delivery_item_id`   bigint(20)    DEFAULT NULL COMMENT '送货单明细ID',
  `sku_id`             bigint(20)    DEFAULT NULL COMMENT 'SKU',
  `product_name`       varchar(200)  NOT NULL COMMENT '商品名称快照',
  `product_spec`       varchar(200)  DEFAULT NULL COMMENT '规格快照',
  `product_unit`       varchar(50)   DEFAULT NULL COMMENT '单位快照',
  `delivered_quantity` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '送货数量（基线=调整后订单行数量）',
  `actual_quantity`    decimal(10,2) NOT NULL DEFAULT 0 COMMENT '实收数量（可超送）',
  `unit_price`         decimal(10,2) NOT NULL DEFAULT 0 COMMENT '单价快照',
  `loss_quantity`      decimal(10,2) NOT NULL DEFAULT 0 COMMENT '损耗数量（实收-送货，可为负）',
  `loss_reason`        varchar(500)  DEFAULT NULL COMMENT '负损耗原因（必填）',
  `actual_amount`      decimal(12,2) NOT NULL DEFAULT 0 COMMENT '实收金额（实收×单价）',
  `sort`               int(10)       NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_acceptance_id` (`acceptance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验收单明细表';

-- ------------------------------------------------------------
-- 6. 复用表改造：送货单加打印次数；打印模板加引擎与绑定字段
-- ------------------------------------------------------------
ALTER TABLE `t_delivery_order`
  ADD COLUMN `print_count` int(10) NOT NULL DEFAULT 0 COMMENT '打印次数' AFTER `status`;

ALTER TABLE `t_print_template`
  ADD COLUMN `render_engine` varchar(32)  NOT NULL DEFAULT 'jimureport' COMMENT '渲染引擎（jimureport/hiprint）' AFTER `id`,
  ADD COLUMN `bind_type`     tinyint(3)    NOT NULL DEFAULT 3 COMMENT '绑定类型：1客户+配送点组合 2客户 3全局默认' AFTER `render_engine`,
  ADD COLUMN `delivery_point_id` bigint(20) DEFAULT NULL COMMENT '绑定配送点ID（可空）' AFTER `customer_id`,
  ADD COLUMN `copies`        int(10)       NOT NULL DEFAULT 1 COMMENT '联数（打印份数）' AFTER `delivery_point_id`,
  ADD COLUMN `is_default`    char(1)       NOT NULL DEFAULT '0' COMMENT '是否全局默认模板（0否 1是）' AFTER `copies`;

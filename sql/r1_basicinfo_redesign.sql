-- ============================================================
-- r1 基础信息模块重构（deepseek_redesign.md）
-- 核心变化：SKU 去客户化（t_product_sku 移除 customer_id），
--           新增 customers_sku（客户商品池）、customer_group（客户分组）、
--           delivery_sku_override（配送点覆盖）、default_sku_template（批量赋值模板）
-- 前置条件：项目未上线，允许重建 t_product_sku（旧数据已确认量级极小，不迁移）
-- 说明：旧 t_product_sku 数据（2 条）+ delivery_point_price（1 条）不再迁移，
--       重新录入标准 SKU；delivery_point_price 表在服务层切换后废弃。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 重建 t_product_sku：移除 customer_id，SKU 成为标准商品单元
--    code 全局唯一：S + 8 位数字（S00000001）
--    唯一约束：(category_id, name, spec_name, unit)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_product_sku`;
CREATE TABLE `t_product_sku` (
    `id`              bigint(20)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `spu_id`          bigint(20)              DEFAULT NULL COMMENT '所属SPU（可空）',
    `category_id`     bigint(20)     NOT NULL COMMENT '分类ID',
    `code`            varchar(32)    NOT NULL COMMENT '全局唯一编码（S+8位数字）',
    `name`            varchar(200)   NOT NULL COMMENT '商品名称',
    `mnemonic_code`   varchar(128)            DEFAULT NULL COMMENT '助记码（拼音首字母）',
    `spec_name`       varchar(200)            DEFAULT NULL COMMENT '规格描述（如“大果”“5斤/箱”）',
    `unit`            varchar(20)    NOT NULL COMMENT '固定单位（箱/斤）',
    `is_weighted`     tinyint(1)     NOT NULL DEFAULT '0' COMMENT '是否称重商品（1=称重，0=非称重）',
    `base_unit`       varchar(20)             DEFAULT NULL COMMENT '基础单位（可选，跨SKU汇总用）',
    `conversion_rate` decimal(10,4)           DEFAULT NULL COMMENT '与基础单位的换算率',
    `sale_price`      decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '参考售价（仅展示，非交易价格）',
    `saleable`        tinyint(1)     NOT NULL DEFAULT '1' COMMENT '是否上架',
    `valid`           tinyint(1)     NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`      tinyint(1)     NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`       varchar(64)              DEFAULT '' COMMENT '创建者',
    `create_time`     timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       varchar(64)              DEFAULT '' COMMENT '更新者',
    `update_time`     datetime                DEFAULT NULL COMMENT '更新时间',
    `remark`          varchar(500)            DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    UNIQUE KEY `uk_category_name_spec_unit` (`category_id`, `name`, `spec_name`, `unit`),
    KEY `idx_spu_id` (`spu_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_saleable` (`saleable`),
    KEY `idx_valid` (`valid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准商品SKU表（客户无关）';

-- ------------------------------------------------------------
-- 2. 新增 customers_sku：客户商品池（客户对标准SKU的个性化）
--    唯一约束：(customer_id, sku_id)；customer_code 全局唯一
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `customers_sku` (
    `id`                bigint(20)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`       bigint(20)     NOT NULL COMMENT '客户ID',
    `sku_id`            bigint(20)     NOT NULL COMMENT '标准SKU ID',
    `alias`             varchar(200)            DEFAULT NULL COMMENT '客户自定义商品别名',
    `customer_code`     varchar(64)    NOT NULL COMMENT '客户商品编码（C{客户ID}+6位自增）',
    `unit`              varchar(20)             DEFAULT NULL COMMENT '客户下单单位（默认同SKU单位）',
    `min_order_qty`     decimal(10,2)  NOT NULL DEFAULT '1.00' COMMENT '最小起订量',
    `order_step`        decimal(10,2)  NOT NULL DEFAULT '1.00' COMMENT '下单步长',
    `is_follow_default` tinyint(1)     NOT NULL DEFAULT '1' COMMENT '是否跟随默认模板（1=是，0=已个性化）',
    `source_template_id` bigint(20)             DEFAULT NULL COMMENT '来源模板ID',
    `status`            tinyint(1)     NOT NULL DEFAULT '1' COMMENT '状态（1可用 0停用）',
    `created_at`        datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        datetime                DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_sku` (`customer_id`, `sku_id`),
    UNIQUE KEY `uk_customer_code` (`customer_code`),
    KEY `idx_sku_id` (`sku_id`),
    KEY `idx_customer_status` (`customer_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户商品表（客户对SKU的个性化与商品池）';

-- ------------------------------------------------------------
-- 3. 新增 customer_group：客户分组（默认模板按分组适配）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `customer_group` (
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(50)  NOT NULL COMMENT '分组名称（如“批发”“食堂”）',
    `is_deleted`  tinyint(1)   NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`   varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户分组表';

-- t_customer 增加 group_id（可空，现有客户无分组）；幂等处理（MySQL 5.7 无 ADD COLUMN IF NOT EXISTS）
DROP PROCEDURE IF EXISTS `r1_add_customer_group_id`;
DELIMITER $$
CREATE PROCEDURE `r1_add_customer_group_id`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_customer' AND COLUMN_NAME = 'group_id') THEN
        ALTER TABLE `t_customer` ADD COLUMN `group_id` bigint(20) DEFAULT NULL COMMENT '客户分组ID（关联 customer_group.id）' AFTER `alias`;
        ALTER TABLE `t_customer` ADD KEY `idx_group_id` (`group_id`);
    END IF;
END$$
DELIMITER ;
CALL `r1_add_customer_group_id`();
DROP PROCEDURE `r1_add_customer_group_id`;

-- ------------------------------------------------------------
-- 4. 新增 delivery_sku_override：配送点级覆盖（价格/别名/可见性）
--    整合原 delivery_point_price；唯一 (delivery_point_id, sku_id)，
--    生效/失效日期管理有效期，取价取最新生效
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `delivery_sku_override` (
    `id`               bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `delivery_point_id` bigint(20)   NOT NULL COMMENT '配送点ID（关联 t_customer_dept.id）',
    `sku_id`           bigint(20)    NOT NULL COMMENT '标准SKU ID',
    `is_available`     tinyint(1)    NOT NULL DEFAULT '1' COMMENT '是否可用（1=可见，0=隐藏）',
    `price_override`   decimal(10,2)          DEFAULT NULL COMMENT '价格覆盖（空则继承客户级价格）',
    `alias_override`   varchar(200)           DEFAULT NULL COMMENT '别名覆盖（可空）',
    `effective_date`   date                   DEFAULT NULL COMMENT '生效日期（可空）',
    `expire_date`      date                   DEFAULT NULL COMMENT '失效日期（可空）',
    `created_at`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       datetime               DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_delivery_sku` (`delivery_point_id`, `sku_id`),
    KEY `idx_sku_id` (`sku_id`),
    KEY `idx_available` (`is_available`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送点商品覆盖表（价格/别名/可见性）';

-- ------------------------------------------------------------
-- 5. 新增 default_sku_template + default_sku_template_item：批量赋值默认SKU模板
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `default_sku_template` (
    `id`                bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`              varchar(100) NOT NULL COMMENT '模板名称（如“食堂常用商品”）',
    `customer_group_id` bigint(20)  DEFAULT NULL COMMENT '适用客户分组（可空=全部）',
    `status`            tinyint(1)  NOT NULL DEFAULT '1' COMMENT '状态（1启用 0停用）',
    `created_at`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        datetime             DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_customer_group` (`customer_group_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='默认SKU模板（批量赋值用，不含价格）';

CREATE TABLE IF NOT EXISTS `default_sku_template_item` (
    `id`          bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_id` bigint(20) NOT NULL COMMENT '模板ID',
    `sku_id`      bigint(20) NOT NULL COMMENT '标准SKU ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_sku` (`template_id`, `sku_id`),
    KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='默认SKU模板明细';

-- ------------------------------------------------------------
-- 6. temp_product 增强：支持客户专用临时商品 + 转正标记（幂等）
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS `r1_add_temp_product_cols`;
DELIMITER $$
CREATE PROCEDURE `r1_add_temp_product_cols`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'temp_product' AND COLUMN_NAME = 'customer_id') THEN
        ALTER TABLE `temp_product`
            ADD COLUMN `customer_id` bigint(20) DEFAULT NULL COMMENT '关联客户ID（可空=全局临时商品）' AFTER `id`,
            ADD COLUMN `converted_sku_id` bigint(20) DEFAULT NULL COMMENT '转正后标准SKU ID（可空=未转正）' AFTER `customer_id`,
            ADD KEY `idx_customer_id` (`customer_id`),
            ADD KEY `idx_converted_sku` (`converted_sku_id`);
    END IF;
END$$
DELIMITER ;
CALL `r1_add_temp_product_cols`();
DROP PROCEDURE `r1_add_temp_product_cols`;

-- 商品分类表
CREATE TABLE `t_product_category`
(
    `id`          bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(200) NOT NULL COMMENT '分类名称',
    `parent_id`   bigint(10) unsigned DEFAULT NULL COMMENT '上级分类ID',
    `code`        char(10)              DEFAULT NULL COMMENT '分类编号',
    `level`       tinyint(2) NOT NULL COMMENT '分类级别(1级最大)',
    `sort`        int(10) unsigned NOT NULL DEFAULT '0' COMMENT '分类排序',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`   varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_name` (`name`),
    KEY           `idx_parent_id` (`parent_id`) USING BTREE,
    KEY           `idx_sort` (`sort`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品分类表';

-- 商品spu表
CREATE TABLE `t_product_spu`
(
    `id`            bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_id`   bigint(10) unsigned NOT NULL COMMENT '分类ID',
    `name`          varchar(200) NOT NULL COMMENT '商品名称',
    `description`   varchar(200) DEFAULT NULL COMMENT '商品描述',
    `mnemonic_code` varchar(128) NOT NULL COMMENT '助记码',
    `images`        json                  DEFAULT NULL COMMENT '商品图片',
    `saleable`      tinyint(1) NOT NULL COMMENT '是否上架',
    `sort`          int(10) unsigned NOT NULL DEFAULT '0' COMMENT '商品排序',
    `valid`         tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`    tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`     varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`   datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`        varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_category_id_name` (`category_id`, `name`),
    KEY             `idx_mnemonic_code` (`mnemonic_code`) USING BTREE,
    KEY             `idx_remark` (`remark`) USING BTREE,
    KEY             `idx_saleable` (`saleable`) USING BTREE,
    KEY             `idx_valid` (`valid`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品spu表';

-- 客户表
CREATE TABLE `t_customer`
(
    `id`          bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(200) NOT NULL COMMENT '客户名称',
    `alias`       varchar(200) DEFAULT NULL COMMENT '客户别名',
    `type`        varchar(10)  NOT NULL COMMENT '客户类型',
    `tel`         char(11)              DEFAULT NULL COMMENT '手机号',
    `address`     varchar(200)          DEFAULT NULL COMMENT '客户地址',
    `valid`       tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`   varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `unq_name` (`name`) USING BTREE,
    KEY           `idx_alias` (`alias`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='客户表';

-- 客户部门表
CREATE TABLE `t_customer_dept`
(
    `id`            bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`   bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `parent_id`     bigint(10) unsigned NOT NULL COMMENT '上级部门ID',
    `code`          varchar(200) NOT NULL COMMENT '部门编号',
    `name`          varchar(200) NOT NULL COMMENT '部门名称',
    `mnemonic_code` varchar(128) NOT NULL COMMENT '助记码',
    `address`       varchar(200)          DEFAULT NULL COMMENT '客户配送地址',
    `location`      GEOMETRY              DEFAULT NULL COMMENT '位置坐标',
    `valid`         tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`    tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`     varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`   datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`        varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `unq_code` (`code`) USING BTREE,
    KEY           `idx_name` (`name`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='客户部门表';

-- 供应商表
CREATE TABLE `t_supplier`
(
    `id`          bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(200) NOT NULL COMMENT '供应商名称',
    `alias`       varchar(200) NOT NULL COMMENT '供应商别名',
    `tel`         char(11)              DEFAULT NULL COMMENT '手机号',
    `valid`       tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`   varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `unq_name` (`name`) USING BTREE,
    KEY           `idx_alias` (`alias`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='供应商表';

-- 供应商明细表
CREATE TABLE `t_supplier_detail`
(
    `id`            bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `supplier_id`  bigint(10) unsigned NOT NULL COMMENT '供应商ID',
    `spu_id`        bigint(10) unsigned NOT NULL COMMENT '产品ID',
    `sku_id`        bigint(10) unsigned NOT NULL COMMENT '商品ID',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`   varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY             `idx_spu_id` (`spu_id`) USING BTREE,
    KEY             `idx_sku_id` (`sku_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='供应商明细表';

-- 商品sku表
CREATE TABLE `t_product_sku`
(
    `id`            bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`   bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `spu_id`        bigint(10) unsigned NOT NULL COMMENT '产品ID',
    `code`          varchar(200) NOT NULL COMMENT '商品编号',
    `name`          varchar(200) NOT NULL COMMENT '商品名称',
    `mnemonic_code` varchar(128) NOT NULL COMMENT '助记码',
    `unit`          varchar(20)  NOT NULL COMMENT '商品单位',
    `spec`          varchar(200)          DEFAULT NULL COMMENT '商品规格',
    `images`        json                  DEFAULT NULL COMMENT '商品图片',
    `properties`    json                  DEFAULT NULL COMMENT '商品参数',
    `sale_price`    decimal(10, 2) unsigned NOT NULL DEFAULT '0' COMMENT '商品售价',
    `visit_count`   bigint(10) unsigned DEFAULT NULL COMMENT '下单次数',
    `saleable`      tinyint(1) NOT NULL COMMENT '是否上架',
    `valid`         tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`    tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`     varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`   datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`        varchar(500)          DEFAULT NULL COMMENT '商品备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY             `idx_spu_id` (`spu_id`) USING BTREE,
    KEY             `idx_customer_id_spu_id` (`customer_id`, `spu_id`) USING BTREE,
    UNIQUE KEY `idx_customer_id_name_unit` (`customer_id`, `name`, `unit`) USING BTREE,
    KEY             `idx_saleable` (`saleable`) USING BTREE,
    KEY             `idx_valid` (`valid`) USING BTREE,
    KEY             `idx_code` (`code`) USING BTREE,
    KEY             `idx_remark` (`remark`) USING BTREE,
    FULLTEXT KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品sku表';

-- 商品报价表
CREATE TABLE `t_product_sku_quote`
(
    `id`                   bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`          bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `effective_start_date` datetime NOT NULL COMMENT '报价生效时间',
    `effective_end_date`   datetime NOT NULL COMMENT '报价结束时间',
    `valid`                tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`           tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `version`   int(10) unsigned NOT NULL DEFAULT '0' COMMENT '版本号',
    `create_by`            varchar(64)        DEFAULT '' COMMENT '创建者',
    `create_time`          timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            varchar(64)        DEFAULT '' COMMENT '更新者',
    `update_time`          datetime           DEFAULT NULL COMMENT '更新时间',
    `remark`               varchar(500)       DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY                    `idx_customer_id` (`customer_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品报价表';

-- 商品报价明细表
CREATE TABLE `t_product_sku_quote_detail`
(
    `id`           bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`  bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `quote_id`     bigint(10) unsigned NOT NULL COMMENT '商品报价ID',
    `sku_id`       bigint(10) unsigned NOT NULL COMMENT '商品ID',
    `product_name` varchar(200) NOT NULL COMMENT '商品名称',
    `product_unit` varchar(20)  NOT NULL COMMENT '商品单位',
    `product_spec` varchar(200)          DEFAULT NULL COMMENT '商品参数',
    `price`        decimal(10, 2) unsigned NOT NULL DEFAULT '0' COMMENT '商品报价',
    `valid`        tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`   tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `version`   int(10) unsigned NOT NULL DEFAULT '0' COMMENT '版本号',
    `create_by`    varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`  datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`       varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY            `idx_customer_id` (`customer_id`) USING BTREE,
    KEY            `idx_quote_id` (`quote_id`) USING BTREE,
    KEY            `idx_sku_id` (`sku_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品报价明细表';

-- 销售订单表
CREATE TABLE `t_sale_order`
(
    `id`               bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`      bigint(10) unsigned DEFAULT NULL COMMENT '客户ID',
    `customer_dept_id` bigint(10) unsigned DEFAULT NULL COMMENT '客户部门ID',
    `code`             varchar(200) NOT NULL COMMENT '订单编号',
    `source`           tinyint(3) unsigned NOT NULL COMMENT '订单来源：1后台下单,2线上下单',
    `type`             tinyint(3) unsigned NOT NULL COMMENT '订单来源：1正常订单,2加单',
    `amount`           decimal(10, 2) unsigned NOT NULL COMMENT '总金额',
    `status`           tinyint(3) unsigned NOT NULL COMMENT '状态：0制单,1审核,2送货,3验收,4完成',
    `deliver_date`     date         NOT NULL COMMENT '预计配送日期',
    `is_deleted`       tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `version`   int(10) unsigned NOT NULL DEFAULT '0' COMMENT '版本号',
    `create_by`        varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`      datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`           varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `unq_code` (`code`) USING BTREE,
    KEY                `idx_code` (`code`) USING BTREE,
    KEY                `idx_customer_id` (`customer_id`) USING BTREE,
    KEY                `idx_customer_dept_id` (`customer_dept_id`) USING BTREE,
    KEY                `idx_status` (`status`) USING BTREE,
    KEY                `idx_deliver_date` (`deliver_date`) USING BTREE,
    KEY                `idx_create_time` (`create_time`) USING BTREE,
    KEY                `idx_type` (`type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='销售订单表';

-- 销售订单详情表
CREATE TABLE `t_sale_order_detail`
(
    `id`               bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`         bigint(10) unsigned NOT NULL COMMENT '订单ID',
    `customer_id`      bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `customer_dept_id` bigint(10) unsigned NOT NULL COMMENT '客户部门ID',
    `sku_id`           bigint(10) unsigned DEFAULT NULL COMMENT '商品ID（临时添加，可为空）',
    `order_code`       varchar(200) NOT NULL COMMENT '订单编号',
    `product_name`     varchar(200) NOT NULL COMMENT '商品名称',
    `product_unit`     varchar(20)           DEFAULT NULL COMMENT '商品单位（可为空）',
    `product_price`    decimal(10, 2) unsigned NOT NULL DEFAULT '0' COMMENT '商品单价',
    `product_spec`     varchar(200)          DEFAULT NULL COMMENT '商品规格',
    `num`              decimal(10, 2) unsigned NOT NULL COMMENT '计划数量',
    `expect_amount`    decimal(10, 2) unsigned DEFAULT '0' COMMENT '计划总金额',
    `actual_price`    decimal(10, 2) unsigned NOT NULL DEFAULT '0' COMMENT '验收商品单价',
    `actual_num`       decimal(10, 2) unsigned DEFAULT '0' COMMENT '验收数量',
    `actual_amount`    decimal(10, 2) unsigned DEFAULT '0' COMMENT '验收总金额',
    `sort`        int(10) unsigned NOT NULL DEFAULT '0' COMMENT '订单详情排序',
    `is_deleted`       tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `version`   int(10) unsigned NOT NULL DEFAULT '0' COMMENT '版本号',
    `create_by`        varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`      datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`           varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY (`order_id`, `sku_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='销售订单详情表';

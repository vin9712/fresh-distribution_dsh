-- 产品分类表
CREATE TABLE `t_category`
(
    `id`          bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(200) NOT NULL COMMENT '分类名称',
    `parent_id`   bigint(10) unsigned DEFAULT NULL COMMENT '上级分类ID',
    `code`        char(10)     DEFAULT NULL COMMENT '分类编号',
    `level`       tinyint(2) NOT NULL COMMENT '分类级别(1级最大)',
    `sort`        int(10) unsigned NOT NULL COMMENT '排名指数',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`   varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time` datetime     DEFAULT NOT NULL CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY           `idx_parent_id` (`parent_id`) USING BTREE,
    KEY           `idx_sort` (`sort`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='产品分类表';

-- 产品表
CREATE TABLE `t_spu`
(
    `id`          bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`       varchar(200) NOT NULL COMMENT '标题',
    `sub_title`   varchar(200)          DEFAULT NULL COMMENT '副标题',
    `category_id` bigint(10) unsigned NOT NULL COMMENT '分类ID',
    `saleable`    tinyint(1) NOT NULL COMMENT '是否上架',
    `valid`       tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`   varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_category_id_title` (`category_id`, `title`),
    KEY           `idx_saleable` (`saleable`) USING BTREE,
    KEY           `idx_valid` (`valid`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='产品表';

-- 客户表
CREATE TABLE `t_customer`
(
    `id`          bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(200) NOT NULL COMMENT '客户名称',
    `alias`       varchar(200) NOT NULL COMMENT '客户别名',
    `type`        varchar(10)  NOT NULL COMMENT '客户类型',
    `tel`         char(11)              DEFAULT NULL COMMENT '手机号',
    `adress`      varchar(200)          DEFAULT NULL COMMENT '客户地址',
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

-- 客户配送点表
CREATE TABLE `t_customer_address`
(
    `id`            bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`   bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `parent_id`     bigint(10) unsigned NOT NULL COMMENT '上级配送点ID',
    `name`          varchar(200) NOT NULL COMMENT '配送点名称',
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
    UNIQUE INDEX `unq_name` (`name`) USING BTREE,
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='客户配送点表';

-- 商品单元表
CREATE TABLE `t_sku`
(
    `id`            bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`   bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `spu_id`        bigint(10) unsigned NOT NULL COMMENT '产品ID',
    `name`          varchar(200) NOT NULL COMMENT '商品名称',
    `mnemonic_code` varchar(128) NOT NULL COMMENT '助记码',
    `unit`          varchar(20)  NOT NULL COMMENT '商品单位',
    `images`        json                  DEFAULT NULL COMMENT '商品图片',
    `price`         decimal(10, 2) unsigned NOT NULL DEFAULT '0' COMMENT '价格',
    `param`         json         NOT NULL COMMENT '商品参数',
    `visit_count`   int(10) unsigned DEFAULT NULL COMMENT '下单次数',
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
    FULLTEXT KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品单元表';

-- 商品报价表
CREATE TABLE `t_sku_quote`
(
    `id`                   bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`          bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `effective_start_date` timestamp NOT NULL COMMENT '报价生效时间',
    `effective_end_date`   timestamp NOT NULL COMMENT '报价结束时间',
    `valid`                tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`           tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`            varchar(64)        DEFAULT '' COMMENT '创建者',
    `create_time`          timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            varchar(64)        DEFAULT '' COMMENT '更新者',
    `update_time`          datetime           DEFAULT NULL COMMENT '更新时间',
    `remark`               varchar(500)       DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY                    `idx_customer_id` (`customer_id`) USING BTREE,
    KEY                    `idx_sku_id` (`sku_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品报价表';

-- 商品报价明细表
CREATE TABLE `t_sku_quote_detail`
(
    `id`            bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`   bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `quote_id`      bigint(10) unsigned NOT NULL COMMENT '商品报价ID',
    `sku_id`        bigint(10) unsigned NOT NULL COMMENT '商品ID',
    `product_name`  varchar(200) NOT NULL COMMENT '商品名称',
    `product_unit`  varchar(20)  NOT NULL COMMENT '商品单位',
    `product_param` json         NOT NULL COMMENT '商品参数',
    `price`         decimal(10, 2) unsigned NOT NULL DEFAULT '0' COMMENT '商品报价',
    `valid`         tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`    tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`     varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`   datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`        varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY             `idx_customer_id` (`customer_id`) USING BTREE,
    KEY             `idx_quote_id` (`quote_id`) USING BTREE,
    KEY             `idx_sku_id` (`sku_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品报价明细表';

-- 订单表
CREATE TABLE `t_order`
(
    `id`           bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`         varchar(200) NOT NULL COMMENT '流水号',
    `source`       tinyint(3) unsigned NOT NULL COMMENT '订单来源：1后台下单,2线上下单',
    `type`         tinyint(3) unsigned NOT NULL COMMENT '订单类型：1实体销售,2网络销售',
    `shop_id`      bigint(10) unsigned DEFAULT NULL COMMENT '零售店ID',
    `customer_id`  bigint(10) unsigned DEFAULT NULL COMMENT '会员ID',
    `company_id`   bigint(10) DEFAULT NULL COMMENT '公司ID',
    `amount`       decimal(10, 2) unsigned NOT NULL COMMENT '总金额',
    `payment_type` tinyint(3) unsigned NOT NULL COMMENT '支付方式：1借记卡,2信用卡,3微信,4支付宝,5现金',
    `status`       tinyint(3) unsigned NOT NULL COMMENT '状态：1未付款,2已付款,3已发货,4已签收',
    `postage`      decimal(10, 2) unsigned DEFAULT NULL COMMENT '邮费',
    `weight`       int(10) unsigned DEFAULT NULL COMMENT '重量（克）',
    `voucher_id`   bigint(10) unsigned DEFAULT NULL COMMENT '购物券ID',
    `expect_date`  int(8) DEFAULT NULL COMMENT '预计配送日期',
    `create_time`  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_deleted`   tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `unq_code` (`code`) USING BTREE,
    KEY            `idx_code` (`code`) USING BTREE,
    KEY            `idx_customer_id` (`customer_id`) USING BTREE,
    KEY            `idx_status` (`status`) USING BTREE,
    KEY            `idx_create_time` (`create_time`) USING BTREE,
    KEY            `idx_type` (`type`) USING BTREE,
    KEY            `idx_shop_id` (`shop_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='订单表';

-- 订单详情表
CREATE TABLE `t_order_detail`
(
    `id`                  bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`            bigint(10) unsigned NOT NULL COMMENT '订单ID',
    `customer_id`         bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `customer_address_id` bigint(10) unsigned NOT NULL COMMENT '客户配送点ID',
    `sku_id`              bigint(10) unsigned NOT NULL COMMENT '商品ID',
    `product_name`        varchar(200) NOT NULL COMMENT '商品名称',
    `product_unit`        varchar(20)           DEFAULT NULL COMMENT '商品单位（可为空）',
    `price`               decimal(10, 2) unsigned NOT NULL COMMENT '商品单价',
    `input_num`           varchar(20)  NOT NULL COMMENT '用户输入数量（可能不为数字，则num为0）',
    `num`                 decimal(10, 2) unsigned NOT NULL COMMENT '计划数量',
    `actual_num`          decimal(10, 2) unsigned DEFAULT '0' COMMENT '验收数量',
    `is_deleted`          tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`           varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`           varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`         datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`              varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`order_id`, `sku_id`) USING BTREE,
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='订单详情表';


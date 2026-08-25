package com.lin.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 批量建品入参（SPU→SKU→客户商品→客户映射 幂等建链）
 *
 * 用于报价单导入「批量建品」与临时商品转正两条路径。
 *
 * @author dsh
 */
@Data
public class ProductCreationDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 客户ID（建 customers_sku / customer_sku_mapping 用） */
    private Long customerId;

    /** 标准商品名（人工确认的规范名称，SKU/SPU 命名依据） */
    private String standardName;

    /** 客户原始叫法（写 customers_sku.alias 与 customer_sku_mapping.customer_alias；可空则取标准名） */
    private String alias;

    /** 商品分类ID */
    private Long categoryId;

    /** 单位 */
    private String unit;

    /** 规格（可空） */
    private String spec;

    /** 售价（可空则 0，仅新建 SKU 时生效） */
    private BigDecimal price;
}

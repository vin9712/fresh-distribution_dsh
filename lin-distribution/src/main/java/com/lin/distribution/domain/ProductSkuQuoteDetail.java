package com.lin.distribution.domain;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.Version;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.*;

/**
 * 商品报价明细对象 t_product_sku_quote_detail
 *
 * @author lin
 * @date 2024-11-15
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class ProductSkuQuoteDetail extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 商品报价ID
     */
    private Long quoteId;

    /**
     * 商品ID
     */
    private Long skuId;

    /**
     * 商品名称
     */
    @Excel(name = "商品名称")
    private String productName;

    /**
     * 商品单位
     */
    @Excel(name = "商品单位")
    private String productUnit;

    /**
     * 商品参数
     */
    @Excel(name = "商品参数")
    private String productSpec;

    /**
     * 商品报价
     */
    @Excel(name = "商品报价")
    private BigDecimal price;

    /**
     * 是否有效
     */
    @Excel(name = "是否有效")
    private Integer valid;

    /**
     * 逻辑删除
     */
    private Boolean isDeleted;

    /**
     * 版本号
     */
    @Version
    private Integer version;

}

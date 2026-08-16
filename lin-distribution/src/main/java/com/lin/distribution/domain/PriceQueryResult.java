package com.lin.distribution.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 取价结果
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceQueryResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** SKU */
    private Long skuId;

    /** 命中价格（未命中为 null） */
    private BigDecimal price;

    /** 价格来源：1配送点报价 2客户报价 3报价模板 */
    private Integer source;

    /** 来源记录ID */
    private Long sourceId;

    public static PriceQueryResult empty(Long skuId) {
        return PriceQueryResult.builder().skuId(skuId).price(null).build();
    }
}

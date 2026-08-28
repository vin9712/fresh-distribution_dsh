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

    /** 价格来源：1客户报价（原 2客户报价/3报价模板 已随价格层级简化废弃，见蓝图 W0-1） */
    private Integer source;

    /** 来源记录ID */
    private Long sourceId;

    /** 价格来源：客户正式报价（唯一取价口径） */
    public static final int SOURCE_CUSTOMER_QUOTE = 1;

    public static PriceQueryResult empty(Long skuId) {
        return PriceQueryResult.builder().skuId(skuId).price(null).build();
    }
}

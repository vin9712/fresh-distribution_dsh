package com.lin.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 订单加退换调整创建请求对象
 *
 * @author dsh
 */
@Data
public class OrderAdjustmentCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 订单ID */
    private Long orderId;

    /** 调整类型：1加单 2退单 3换货 */
    private Integer type;

    /** 调整原因 */
    private String reason;

    /** 调整日期（归属D天） */
    private LocalDate adjustDate;

    /** 调整明细 */
    private List<Item> items;

    @Data
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 原订单明细行ID（可空=新增行） */
        private Long orderItemId;

        /** 调整数量（正=加/负=退） */
        private BigDecimal deltaQuantity;

        /** SKU（新增行可空） */
        private Long skuId;

        /** 商品名称（新增行必填） */
        private String productName;

        /** 商品单位（新增行） */
        private String productUnit;

        /** 商品规格（新增行） */
        private String productSpec;

        /** 商品单价（新增行） */
        private BigDecimal productPrice;
    }
}

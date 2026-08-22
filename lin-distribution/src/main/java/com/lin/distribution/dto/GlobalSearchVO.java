package com.lin.distribution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 全局搜索 Ctrl+K 结果（客户 / 商品 / 订单 分组）
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GlobalSearchVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 搜索关键词（回显） */
    private String keyword;

    /** 客户命中的搜索结果 */
    private List<CustomerHit> customers;

    /** 商品（SKU）命中的搜索结果 */
    private List<ProductHit> products;

    /** 销售订单命中的搜索结果 */
    private List<OrderHit> orders;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CustomerHit implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String name;
        private String alias;
        private String tel;
        private String address;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProductHit implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String code;
        private String name;
        private String specName;
        private String unit;
        private String categoryName;
        private BigDecimal salePrice;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderHit implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String code;
        private String customerName;
        private String customerDeptName;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate deliveryDate;
        private BigDecimal amount;
    }
}

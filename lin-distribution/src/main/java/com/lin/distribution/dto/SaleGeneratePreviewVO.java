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
 * 销售订单生成单据汇总预览（按品类分组）
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SaleGeneratePreviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 选中的订单（头信息，供抽屉展示） */
    private List<PreviewOrder> orders;

    /** 按品类分组的商品汇总 */
    private List<PreviewGroup> groups;

    /** 商品总行数（合并后） */
    private Integer itemCount;

    /** 商品总数量 */
    private BigDecimal totalQuantity;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PreviewOrder implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String code;
        private String customerName;
        private String customerDeptName;
        private String deliveryName;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate deliveryDate;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PreviewGroup implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 品类名称（临时商品归入"临时商品"） */
        private String categoryName;
        private List<PreviewItem> items;
        private BigDecimal quantity;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PreviewItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long skuId;
        private String productName;
        private String productSpec;
        private String productUnit;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal amount;
    }
}

package com.lin.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 客户价格表导入确认DTO（按预览确认结果生成报价单）
 *
 * @author lin
 */
@Data
public class QuotePriceImportConfirmDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 客户ID */
    private Long customerId;

    /** 生效日期（默认今天） */
    private LocalDate effectiveStartDate;

    /** 失效日期（默认 9999-12-31） */
    private LocalDate effectiveEndDate;

    /** 未匹配行是否自动转临时商品（默认 true） */
    private Boolean unmatchedToTemp;

    /** 确认的明细行 */
    private List<Row> rows;

    @Data
    public static class Row implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 原始商品叫法（未转临时时使用） */
        private String rawName;

        /** 挂钩的SKU ID（null = 未匹配，走转临时逻辑） */
        private Long skuId;

        /** 单位（可空，空则用SKU默认单位） */
        private String unit;

        /** 价格 */
        private BigDecimal price;
    }
}

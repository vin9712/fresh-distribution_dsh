package com.lin.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 客户价格表导入预览请求/结果行
 *
 * @author lin
 */
public class QuotePriceImportDTO {

    private QuotePriceImportDTO() {
    }

    /** 导入预览请求 */
    @Data
    public static class PreviewReq implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 客户ID */
        private Long customerId;

        /** 粘贴的价格表文本（每行一条：商品叫法 价格） */
        private String text;
    }

    /** 预览结果行 */
    @Data
    public static class Row implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 行号（文本中的第几行） */
        private Integer lineNo;

        /** 原始商品叫法 */
        private String rawName;

        /** 解析出的单位（可空） */
        private String unit;

        /** 解析出的价格 */
        private BigDecimal price;

        /** 匹配到的SKU ID（未匹配为 null） */
        private Long skuId;

        /** 匹配到的SKU名称 */
        private String skuName;

        /** 匹配到的SKU规格 */
        private String skuSpec;

        /** 匹配到的SKU单位 */
        private String skuUnit;

        /** 匹配方式：名称/助记码/全局别名/客户映射 */
        private String matchType;

        /** 是否匹配成功 */
        private Boolean matched;

        /** 失败原因（未识别到价格等） */
        private String error;
    }
}

package com.lin.distribution.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户商品粘贴文本快速同步DTO
 *
 * @author lin
 */
@Data
public class CustomerSkuSyncTextDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 客户ID */
    private Long customerId;

    /** 粘贴的商品清单文本（每行一条，支持「客户叫法=内部商品名」或直接「商品名」） */
    private String text;

    /** 未匹配到标准SKU时是否自动转为临时商品（默认 true） */
    private Boolean unmatchedToTemp;
}

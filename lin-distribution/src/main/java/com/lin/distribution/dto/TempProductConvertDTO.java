package com.lin.distribution.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 临时商品转正为正式SKU请求对象
 *
 * @author lin
 * @date 2024-11-20
 */
@Data
public class TempProductConvertDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 临时商品主键 */
    private Long id;

    /** 转正后归属客户ID（不传则 0） */
    private Long customerId;

    /** 商品分类ID（不传则 0） */
    private Long categoryId;

    /** 助记码（不传则由商品名称首字母生成） */
    private String mnemonicCode;
}

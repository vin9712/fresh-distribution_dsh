package com.lin.distribution.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 商品库粘贴文本快速导入DTO
 *
 * @author lin
 */
@Data
public class ProductSpuTextImportDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 粘贴的文本内容（每行一条：支持「分类/商品名」或直接「商品名」） */
    private String text;

    /** 默认分类ID（行内未指定分类时使用，可为空） */
    private Long defaultCategoryId;
}

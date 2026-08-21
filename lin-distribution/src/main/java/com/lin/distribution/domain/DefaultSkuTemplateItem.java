package com.lin.distribution.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 默认SKU模板明细对象 default_sku_template_item
 *
 * @author dsh
 */
@Data
public class DefaultSkuTemplateItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 模板ID */
    private Long templateId;

    /** 标准SKU ID */
    private Long skuId;
}

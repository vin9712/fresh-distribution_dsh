package com.lin.distribution.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.annotation.Excel;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 报价模板SKU价格对象 price_template_sku
 *
 * @author dsh
 */
@Data
public class PriceTemplateSku implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 模板ID */
    private Long templateId;

    /** SKU */
    @Excel(name = "SKU ID")
    private Long skuId;

    /** 商品名称（关联查询展示，非表字段） */
    private String skuName;

    /** 单价 */
    @Excel(name = "单价", cellType = Excel.ColumnType.NUMERIC, scale = 2)
    private BigDecimal unitPrice;

    /** 生效日期 */
    @Excel(name = "生效日期", width = 30, dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date effectiveDate;

    /** 失效日期 */
    @Excel(name = "失效日期", width = 30, dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date expireDate;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}

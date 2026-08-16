package com.lin.distribution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.annotation.Excel;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 客户报价导入行（多客户多行，按客户聚合生成报价单）
 *
 * @author dsh
 */
@Data
public class ProductSkuQuoteImportDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 客户ID */
    @Excel(name = "客户ID")
    private Long customerId;

    /** SKU ID */
    @Excel(name = "SKU ID")
    private Long skuId;

    /** 单价 */
    @Excel(name = "单价", cellType = Excel.ColumnType.NUMERIC, scale = 2)
    private BigDecimal price;

    /** 生效日期 */
    @Excel(name = "生效日期", width = 30, dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date effectiveStartDate;

    /** 失效日期 */
    @Excel(name = "失效日期", width = 30, dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date effectiveEndDate;
}

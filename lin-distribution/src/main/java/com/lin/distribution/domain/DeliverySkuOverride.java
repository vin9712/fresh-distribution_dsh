package com.lin.distribution.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.annotation.Excel;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 配送点商品覆盖对象 delivery_sku_override
 *
 * @author dsh
 */
@Data
public class DeliverySkuOverride implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 配送点ID（t_customer_dept.id） */
    @Excel(name = "配送点ID")
    private Long deliveryPointId;

    /** 配送点名称（关联查询展示，非表字段） */
    @TableField(exist = false)
    private String deliveryPointName;

    /** 标准SKU ID */
    @Excel(name = "SKU ID")
    private Long skuId;

    /** 商品名称（关联查询展示，非表字段） */
    @TableField(exist = false)
    private String skuName;

    /** 是否可用（1=可见，0=隐藏） */
    private Integer isAvailable;

    /** 价格覆盖（空则继承客户级价格） */
    @Excel(name = "价格覆盖", cellType = Excel.ColumnType.NUMERIC, scale = 2)
    private BigDecimal priceOverride;

    /** 别名覆盖（可空） */
    private String aliasOverride;

    /** 生效日期 */
    @Excel(name = "生效日期", width = 30, dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date effectiveDate;

    /** 失效日期 */
    @Excel(name = "失效日期", width = 30, dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date expireDate;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}

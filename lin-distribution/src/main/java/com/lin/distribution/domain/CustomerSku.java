package com.lin.distribution.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.annotation.Excel;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 客户商品对象 customers_sku（客户对标准SKU的个性化）
 *
 * @author dsh
 */
@Data
public class CustomerSku implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 客户ID */
    @Excel(name = "客户ID")
    private Long customerId;

    /** 标准SKU ID */
    @Excel(name = "SKU ID")
    private Long skuId;

    /** 限定配送点ID(t_customer_dept.id)，空=客户通用 */
    private Long deptId;

    /** 客户自定义商品别名 */
    @Excel(name = "客户商品别名")
    private String alias;

    /** 客户商品编码（C{客户ID}+6位自增） */
    @Excel(name = "客户商品编码")
    private String customerCode;

    /** 客户下单单位（默认同SKU单位） */
    private String unit;

    /** 最小起订量 */
    private BigDecimal minOrderQty;

    /** 下单步长 */
    private BigDecimal orderStep;

    /** 是否跟随默认模板（1=是，0=已个性化） */
    private Integer isFollowDefault;

    /** 来源模板ID */
    private Long sourceTemplateId;

    /** 状态（1可用 0停用） */
    private Integer status;

    /** 搜索关键字（非表字段） */
    @TableField(exist = false)
    private String keyword;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** 标准SKU信息（关联查询展示，非表字段） */
    @TableField(exist = false)
    private String skuName;
    @TableField(exist = false)
    private String skuCode;
    @TableField(exist = false)
    private String skuSpecName;
    @TableField(exist = false)
    private String skuUnit;
    @TableField(exist = false)
    private Integer skuIsWeighted;
    @TableField(exist = false)
    private BigDecimal skuSalePrice;
    @TableField(exist = false)
    private Long skuCategoryId;
    @TableField(exist = false)
    private String skuMnemonicCode;
    @TableField(exist = false)
    private String categoryName;

    /** 查询参数：下单时传入的配送点ID，用于过滤可见商品池 */
    @TableField(exist = false)
    private Long deliveryPointId;

    /** 查询参数：true=按配送点精确匹配（管理页）；空/false=白名单并集（通用池∪本点专属，下单用） */
    @TableField(exist = false)
    private Boolean deptExactFilter;
}

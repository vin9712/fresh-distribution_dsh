package com.lin.distribution.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.annotation.Excel;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.Date;

/**
 * 客户SKU映射对象 customer_sku_mapping
 *
 * @author lin
 * @date 2024-11-20
 */
@Data
public class CustomerSkuMapping implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 客户ID */
    @Excel(name = "客户ID")
    private Long customerId;

    /** 客户侧叫法/编码 */
    @Excel(name = "客户叫法")
    private String customerAlias;

    /** 我方SKU */
    @Excel(name = "我方SKU")
    private Long skuId;

    /** 客户名称（来自 customer 表） */
    @TableField(exist = false)
    private String customerName;

    /** 我方SKU名称（来自 sku 表） */
    @TableField(exist = false)
    private String skuName;

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

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("customerId", getCustomerId())
            .append("customerAlias", getCustomerAlias())
            .append("skuId", getSkuId())
            .append("customerName", getCustomerName())
            .append("skuName", getSkuName())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}

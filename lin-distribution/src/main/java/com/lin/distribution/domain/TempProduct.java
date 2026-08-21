package com.lin.distribution.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.annotation.Excel;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 临时商品对象 temp_product
 *
 * @author lin
 * @date 2024-11-20
 */
@Data
public class TempProduct implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 关联客户ID（可空=全局临时商品） */
    private Long customerId;

    /** 转正后标准SKU ID（可空=未转正） */
    private Long convertedSkuId;

    /** 临时商品名称 */
    @Excel(name = "临时商品名称")
    private String name;

    /** 规格 */
    @Excel(name = "规格")
    private String spec;

    /** 单位 */
    @Excel(name = "单位")
    private String unit;

    /** 默认单价 */
    @Excel(name = "默认单价", cellType = Excel.ColumnType.NUMERIC, scale = 2)
    private BigDecimal defaultPrice;

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

    /** 备注 */
    private String remark;

    /** 是否包含已转正记录（非表字段，true=包含，默认只查未转正） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean showAll;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("name", getName())
            .append("spec", getSpec())
            .append("unit", getUnit())
            .append("defaultPrice", getDefaultPrice())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}

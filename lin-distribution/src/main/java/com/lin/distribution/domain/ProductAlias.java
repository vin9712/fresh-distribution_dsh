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
 * 商品全局别名对象 product_alias
 *
 * @author lin
 * @date 2024-11-20
 */
@Data
public class ProductAlias implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 别名类型：1名称 2拼音 3英文缩写 */
    @Excel(name = "别名类型", readConverterExp = "1=名称,2=拼音,3=英文缩写")
    private Integer aliasType;

    /** 别名内容 */
    @Excel(name = "别名内容")
    private String alias;

    /** 关联SKU */
    @Excel(name = "关联SKU")
    private Long skuId;

    /** 关联SKU名称（来自 sku 表） */
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
            .append("aliasType", getAliasType())
            .append("alias", getAlias())
            .append("skuId", getSkuId())
            .append("skuName", getSkuName())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}

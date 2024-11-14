package com.lin.distribution.domain;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableField;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 商品信息对象 t_product_sku
 *
 * @author lin
 * @date 2024-11-11
 */
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class ProductSku extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 产品ID
     */
    private Long spuId;


    /**
     * 商品编码
     * spu简写 + customerId + spuId + 5位自增序号
     */
    @Excel(name = "商品编码")
    private String code;
    /**
     * 商品名称
     */
    @Excel(name = "商品名称")
    private String name;

    /**
     * 助记码
     */
    private String mnemonicCode;

    /**
     * 商品单位
     */
    @Excel(name = "商品单位")
    private String unit;

    /**
     * 商品规格
     */
    @Excel(name = "商品规格")
    private String spec;

    /**
     * 商品图片
     */
    private String images;

    /**
     * 商品参数
     */
    private String properties;

    /**
     * 商品售价
     */
    @Excel(name = "商品售价")
    private BigDecimal salePrice;

    /**
     * 下单次数
     */
    private String visitCount;

    /**
     * 是否上架
     */
    @Excel(name = "是否上架")
    private Integer saleable;

    /**
     * 是否有效
     */
    @Excel(name = "是否有效")
    private Integer valid;

    /**
     * 逻辑删除
     */
    private Boolean isDeleted;

    /**
     * 商品分类ID(来自 spu 表)
     */
    @TableField(exist = false)
    private Long categoryId;


    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("customerId", getCustomerId())
                .append("spuId", getSpuId())
                .append("name", getName())
                .append("mnemonicCode", getMnemonicCode())
                .append("unit", getUnit())
                .append("spec", getSpec())
                .append("images", getImages())
                .append("properties", getProperties())
                .append("salePrice", getSalePrice())
                .append("visitCount", getVisitCount())
                .append("saleable", getSaleable())
                .append("valid", getValid())
                .append("isDeleted", getIsDeleted())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
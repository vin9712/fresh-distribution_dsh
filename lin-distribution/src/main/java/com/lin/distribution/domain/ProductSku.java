package com.lin.distribution.domain;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableField;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 商品信息对象 t_product_sku
 *
 * @author lin
 * @date 2024-11-11
 */
@EqualsAndHashCode(callSuper = true)
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
     * 产品ID(可为空)
     */
    private Long spuId;

    /**
     * 商品分类ID
     */
    private Long categoryId;


    /**
     * 商品编码
     * 客户简写(前四位) + customerId + 5位自增序号
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
     * 商品分类(来自 category 表)
     */
    @TableField(exist = false)
    private String categoryName;

}
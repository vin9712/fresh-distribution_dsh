package com.lin.distribution.domain;

import java.math.BigDecimal;
import java.util.Locale;

import com.baomidou.mybatisplus.annotation.TableField;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import com.lin.common.utils.PinYinConvertUtils;
import lombok.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 标准商品SKU对象 t_product_sku（客户无关）
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
     * 产品ID(可为空)
     */
    private Long spuId;

    /**
     * 商品分类ID
     */
    private Long categoryId;


    /**
     * 商品编码（全局唯一：S + 8位自增序号）
     */
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
     * 规格描述（如“大果”“5斤/箱”）
     */
    @Excel(name = "商品规格")
    private String specName;

    /**
     * 是否称重商品（1=称重，如散装菜；0=非称重，如箱装）
     */
    private Integer isWeighted;

    /**
     * 基础单位（可选，跨SKU汇总）
     */
    private String baseUnit;

    /**
     * 与基础单位的换算率
     */
    private BigDecimal conversionRate;

    /**
     * 商品售价（参考价，仅展示，非交易价格）
     */
    @Excel(name = "商品售价", cellType= Excel.ColumnType.NUMERIC, scale = 2)
    private BigDecimal salePrice;

    /**
     * 是否上架
     */
    private Integer saleable;

    /**
     * 是否有效
     */
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
    /**
     * 商品分类(来自 category 表)
     */
    @Excel(name = "商品分类编号")
    @TableField(exist = false)
    private String categoryCode;
    /**
     * 是否匹配spu
     */
    @TableField(exist = false)
    private String matchedSpu;


    public String getSkuMnemonicCode(){
        return PinYinConvertUtils.toFirstChar(name).toUpperCase(Locale.ROOT);
    }
}

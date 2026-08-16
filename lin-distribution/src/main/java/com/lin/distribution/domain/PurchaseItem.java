package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购单明细对象 purchase_item
 *
 * @author dsh
 */
@Data
public class PurchaseItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 采购单ID */
    @Excel(name = "采购单ID")
    private Long purchaseId;

    /** SKU（临时商品可空） */
    @Excel(name = "SKU ID")
    private Long skuId;

    /** 商品名称快照 */
    @Excel(name = "商品名称")
    private String productName;

    /** 规格快照 */
    @Excel(name = "商品规格")
    private String productSpec;

    /** 单位快照 */
    @Excel(name = "商品单位")
    private String productUnit;

    /** 数量 */
    @Excel(name = "数量")
    private BigDecimal quantity;

    /** 采购单价（成本） */
    @Excel(name = "采购单价")
    private BigDecimal unitPrice;

    /** 小计 */
    @Excel(name = "小计")
    private BigDecimal subtotal;

    /** 排序 */
    @Excel(name = "排序")
    private Integer sort;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("purchaseId", getPurchaseId())
            .append("skuId", getSkuId())
            .append("productName", getProductName())
            .append("productSpec", getProductSpec())
            .append("productUnit", getProductUnit())
            .append("quantity", getQuantity())
            .append("unitPrice", getUnitPrice())
            .append("subtotal", getSubtotal())
            .append("sort", getSort())
            .toString();
    }
}

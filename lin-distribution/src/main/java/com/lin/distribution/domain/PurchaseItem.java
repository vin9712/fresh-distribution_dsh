package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

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

    /** 同商品组内批次序号（1,2,3…；同一 SKU 分批进货各占一行） */
    @Excel(name = "批次序号")
    private Integer batchNo;

    /** SKU（临时商品可空） */
    @Excel(name = "SKU ID")
    private Long skuId;

    /** 本批次供应商ID（可空；同一供应商同一天可录多批） */
    @Excel(name = "供应商ID")
    private Long supplierId;

    /** 本批次供应商名称（直填） */
    @Excel(name = "供应商名称")
    private String supplierName;

    /** 商品名称快照 */
    @Excel(name = "商品名称")
    private String productName;

    /** 规格快照 */
    @Excel(name = "商品规格")
    private String productSpec;

    /** 单位快照 */
    @Excel(name = "商品单位")
    private String productUnit;

    /** 数量（本批次进货数量） */
    @Excel(name = "数量")
    private BigDecimal quantity;

    /** 录入时快照的应采数量（差异审计用） */
    private BigDecimal requiredQty;

    /** 采购单价（进货成本价） */
    @Excel(name = "采购单价")
    private BigDecimal unitPrice;

    /** 小计 */
    @Excel(name = "小计")
    private BigDecimal subtotal;

    /** 排序 */
    @Excel(name = "排序")
    private Integer sort;

    /** 录入人 */
    @Excel(name = "录入人")
    private String createBy;

    /** 录入时间 */
    @Excel(name = "录入时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 批次备注（发票号/车次等） */
    @Excel(name = "批次备注")
    private String remark;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("purchaseId", getPurchaseId())
            .append("batchNo", getBatchNo())
            .append("skuId", getSkuId())
            .append("supplierId", getSupplierId())
            .append("supplierName", getSupplierName())
            .append("productName", getProductName())
            .append("productSpec", getProductSpec())
            .append("productUnit", getProductUnit())
            .append("quantity", getQuantity())
            .append("requiredQty", getRequiredQty())
            .append("unitPrice", getUnitPrice())
            .append("subtotal", getSubtotal())
            .append("sort", getSort())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}

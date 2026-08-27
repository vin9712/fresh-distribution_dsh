package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 验收单明细对象 acceptance_item
 * 实收金额=actual_quantity×unit_price（后端重算）；
 * 差异=实收−送货（正超收/负短收，双向差异均必填原因，S14 v1.1 修订）。
 * S14：loss_quantity 更名 difference_quantity，实体字段同步更名为 differenceQuantity，
 *      保留 @Deprecated getLossQuantity()/setLossQuantity() 转发一个版本以兼容旧 JSON 报文。
 *
 * @author dsh
 */
@Data
public class AcceptanceItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 验收单ID */
    @Excel(name = "验收单ID")
    private Long acceptanceId;

    /** 送货单明细ID */
    @Excel(name = "送货单明细ID")
    private Long deliveryItemId;

    /** 明细所属配送点（S14：A类总单按点展开录入即归属，B/C类也填） */
    private Long customerDeptId;

    /** 配送点名称（列表展示用，查询时关联填充，非表字段） */
    private String customerDeptName;

    /** SKU（临时商品可空） */
    @Excel(name = "SKU ID")
    private Long skuId;

    /** SKU 编码（关联 t_product_sku.code；扫码枪扫码定位用，非表字段） */
    @Excel(name = "商品编码")
    private String skuCode;

    /** 商品名称快照 */
    @Excel(name = "商品名称")
    private String productName;

    /** 规格快照 */
    @Excel(name = "商品规格")
    private String productSpec;

    /** 单位快照 */
    @Excel(name = "商品单位")
    private String productUnit;

    /** 送货数量（基线=送货单明细数量） */
    @Excel(name = "送货数量")
    private BigDecimal deliveredQuantity;

    /** 实收数量（可超送） */
    @Excel(name = "实收数量")
    private BigDecimal actualQuantity;

    /** 单价快照 */
    @Excel(name = "单价")
    private BigDecimal unitPrice;

    /** 验收差异 = 实收−送货（正超收/负短收） */
    @Excel(name = "差异数量")
    private BigDecimal differenceQuantity;

    /** 差异原因类型：1短收(acceptance_shortfall_reason) 2超收(acceptance_overage_reason) */
    private Integer reasonType;

    /** 差异原因（短收/超收字典值） */
    @Excel(name = "差异原因")
    private String lossReason;

    /** 实收金额（实收×单价） */
    @Excel(name = "实收金额")
    private BigDecimal actualAmount;

    /** 排序 */
    @Excel(name = "排序")
    private Integer sort;

    /**
     * @deprecated S14 已更名 {@link #differenceQuantity}，保留一个版本兼容旧 JSON/调用方
     */
    @Deprecated
    public BigDecimal getLossQuantity() {
        return differenceQuantity;
    }

    /**
     * @deprecated S14 已更名 {@link #differenceQuantity}，保留一个版本兼容旧 JSON/调用方
     */
    @Deprecated
    public void setLossQuantity(BigDecimal lossQuantity) {
        this.differenceQuantity = lossQuantity;
    }
}

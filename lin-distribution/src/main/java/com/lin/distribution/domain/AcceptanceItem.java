package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 验收单明细对象 acceptance_item
 * 实收金额=actual_quantity×unit_price（后端重算）；损耗=实收−送货（可为负，负值必填原因）
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

    /** 损耗数量（实收-送货，可为负） */
    @Excel(name = "损耗数量")
    private BigDecimal lossQuantity;

    /** 负损耗原因（损耗为负必填） */
    @Excel(name = "损耗原因")
    private String lossReason;

    /** 实收金额（实收×单价） */
    @Excel(name = "实收金额")
    private BigDecimal actualAmount;

    /** 排序 */
    @Excel(name = "排序")
    private Integer sort;
}

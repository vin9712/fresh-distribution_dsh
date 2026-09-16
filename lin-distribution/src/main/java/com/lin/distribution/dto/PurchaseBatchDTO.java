package com.lin.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购批次录入请求（D-057/D-058：一次实际进货 = 一个批次）
 *
 * <p>商品由「sku + 品名 + 规格 + 单位」四元组定位：命中当日应采清单时，品名/规格/单位/应采数量
 * 由后端从应采清单回填（忽略前端传值）；未命中（手动新增商品/临时商品）时快照取前端传值，
 * 应采数量记 0，且商品名称必填。</p>
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseBatchDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** SKU（临时商品可空，此时按 品名+规格+单位 匹配） */
    private Long skuId;

    /** 商品名称（应采清单快照，仅用于定位） */
    private String productName;

    /** 规格（应采清单快照，仅用于定位） */
    private String productSpec;

    /** 单位（应采清单快照，仅用于定位） */
    private String productUnit;

    /** 本批次进货数量（>0） */
    @NotNull(message = "采购数量不能为空")
    @DecimalMin(value = "0.01", message = "采购数量必须大于 0")
    private BigDecimal quantity;

    /** 本批次进货价（>=0） */
    @NotNull(message = "进货价不能为空")
    @DecimalMin(value = "0.00", message = "进货价不能为负")
    private BigDecimal unitPrice;

    /** 本批次供应商ID（可空，同一供应商同一天可录多批） */
    private Long supplierId;

    /** 本批次供应商名称（直填，可空） */
    private String supplierName;

    /** 批次备注（发票号/车次等） */
    private String remark;
}
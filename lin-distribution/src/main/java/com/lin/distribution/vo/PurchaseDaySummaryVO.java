package com.lin.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 采购日应采汇总 VO（D-056：应采清单 = 订单明细实时视图，不落库）
 *
 * <p>行 = 采购汇总键（sku + 品名 + 规格 + 单位）；每行汇总 应采 / 已采 / 待采 / 批次数 / 加权均价 / 金额，
 * 并携带该行全部进货批次明细。采购单未建时 {@code purchaseId=null}，仅返回应采行。</p>
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseDaySummaryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当日采购单ID（未录入过则为 null） */
    private Long purchaseId;

    /** 采购单号 */
    private String code;

    /** 状态：0草稿 1已确认 2已入库 3已作废 */
    private Integer status;

    /** 采购日期（=配送日期） */
    private LocalDate orderDate;

    /** 单头默认供应商ID */
    private Long supplierId;

    /** 单头默认供应商名称 */
    private String supplierName;

    /** 采购员 */
    private String purchaser;

    /** 单头备注 */
    private String remark;

    /** 当日采购总额 */
    private BigDecimal totalAmount;

    /** 应采品种数 */
    private Integer requiredItemCount;

    /** 已录品种数（已采数量 > 0） */
    private Integer purchasedItemCount;

    /** 应采总数量 */
    private BigDecimal requiredQty;

    /** 已采总数量 */
    private BigDecimal purchasedQty;

    /** 待采总数量（可为负=超采） */
    private BigDecimal pendingQty;

    /** 超采行数（待采 < 0） */
    private Integer overCount;

    /** 汇总行 */
    @Builder.Default
    private List<Row> rows = new ArrayList<>();

    /** 应采清单原始行（Mapper 查询结果载体） */
    @Data
    public static class RequiredRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long skuId;
        private String productName;
        private String productSpec;
        private String productUnit;
        private BigDecimal requiredQty;
    }

    /** 汇总行（应采行 ∪ 已录批次行） */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Row implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 采购汇总键（sku + 品名 + 规格 + 单位） */
        private String key;
        private Long skuId;
        private String productName;
        private String productSpec;
        private String productUnit;

        /** 应采数量（订单视图） */
        private BigDecimal requiredQty;
        /** 已采数量（Σ批次数量） */
        private BigDecimal purchasedQty;
        /** 待采数量 = 应采 − 已采（可为负=超采） */
        private BigDecimal pendingQty;
        /** 批次数 */
        private Integer batchCount;
        /** 加权平均进价 = Σ小计 / Σ数量（4 位小数） */
        private BigDecimal avgPrice;
        /** 采购金额 = Σ小计 */
        private BigDecimal amount;
        /** 订单已撤回（应采=0 但仍有批次） */
        private Boolean orphan;

        @Builder.Default
        private List<Batch> batches = new ArrayList<>();
    }

    /** 批次明细 */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Batch implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private Integer batchNo;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private Long supplierId;
        private String supplierName;
        private String createBy;
        private Date createTime;
        private String remark;
    }
}
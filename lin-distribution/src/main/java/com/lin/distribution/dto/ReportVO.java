package com.lin.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 报表视图对象（DESIGN.md §10：销售日报 + 客户对账单）
 *
 * @author dsh
 */
public class ReportVO {

    /**
     * 销售日报：按客户+配送点分组（实收金额、损耗、商品明细）
     */
    @Data
    public static class DailySaleGroup implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long customerId;
        private String customerName;
        private Long deliveryPointId;
        private String deliveryPointName;
        /** 实收金额合计 */
        private BigDecimal totalActualAmount;
        /** 损耗数量合计（实收-送货） */
        private BigDecimal totalLossQuantity;
        /** 损耗金额合计（损耗×单价，负值为亏） */
        private BigDecimal totalLossAmount;
        private List<DailySaleItem> items;
    }

    @Data
    public static class DailySaleItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private String productName;
        private String productSpec;
        private String productUnit;
        private BigDecimal actualQuantity;
        private BigDecimal lossQuantity;
        private BigDecimal unitPrice;
        private BigDecimal actualAmount;
    }

    /**
     * 客户对账单：每张验收单（单号/日期/金额）与商品明细，合计
     */
    @Data
    public static class CustomerStatement implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long customerId;
        private String customerName;
        private LocalDate beginDate;
        private LocalDate endDate;
        /** 期间合计 */
        private BigDecimal totalAmount;
        private List<StatementAcceptance> acceptances;
    }

    @Data
    public static class StatementAcceptance implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long acceptanceId;
        private String code;
        private LocalDate acceptDate;
        private String deliveryCode;
        private BigDecimal totalAmount;
        private List<StatementItem> items;
    }

    @Data
    public static class StatementItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private String productName;
        private String productSpec;
        private String productUnit;
        private BigDecimal actualQuantity;
        private BigDecimal unitPrice;
        private BigDecimal actualAmount;
    }

    /**
     * 经营概览（蓝图 W0-3.3）：周期销售额/采购额/损耗额与周期估算毛利；区分已/未月结；待确认成本不计毛利
     */
    @Data
    public static class OperatingOverview implements Serializable {
        private static final long serialVersionUID = 1L;

        private LocalDate beginDate;
        private LocalDate endDate;
        /** 验收实收合计（已提交验收单，按验收日期归期） */
        private BigDecimal acceptedAmount;
        /** 已月结销售金额（客户该月已月结） */
        private BigDecimal settledAmount;
        /** 未月结销售金额 */
        private BigDecimal unsettledAmount;
        /** 同周期采购金额（采购单按归属日期归期，含已作废之外） */
        private BigDecimal purchaseAmount;
        /** 待确认成本金额（采购单未入库：草稿+已确认） */
        private BigDecimal pendingCostAmount;
        /** 存在待确认成本（此时不计毛利） */
        private boolean hasPendingCost;
        /** 周期估算毛利 = 验收实收 − 同周期采购金额（无待确认成本时给出；口径见蓝图 §7.2） */
        private BigDecimal grossProfit;
    }

    /**
     * 经营概览验收应收的「已月结/未月结」分桶（mapper 聚合行）
     */
    @Data
    public static class OverviewSettleAmount implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 0=未月结 1=已月结 */
        private Integer settled;
        /** 金额 */
        private BigDecimal amount;
    }
}

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
}

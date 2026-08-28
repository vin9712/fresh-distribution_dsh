package com.lin.distribution.vo;

import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.MonthAdjustment;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户月度结算预览（蓝图 W0-3.1）：列出该客户该月「验收单 + 下月调整单」并与汇总
 *
 * @author dsh
 */
@Data
public class MonthSettlementPreviewVO {
    /** 客户ID */
    private Long customerId;
    /** 客户名称 */
    private String customerName;
    /** 结算月份 */
    private String billMonth;

    /** 该月验收单（已提交为结算依据） */
    private List<Acceptance> acceptances = new ArrayList<>();
    /** 该月下月调整单（已提交参与重算） */
    private List<MonthAdjustment> adjustments = new ArrayList<>();

    /** 已提交验收单应收合计（验收实收总额） */
    private BigDecimal acceptedAmount = BigDecimal.ZERO;
    /** 已提交调整单应收调整合计 */
    private BigDecimal adjustmentReceivable = BigDecimal.ZERO;
    /** 已提交调整单采购成本调整合计 */
    private BigDecimal adjustmentCost = BigDecimal.ZERO;
    /** 已提交调整单净应收变动 = 应收调整 − 成本调整（经营概览口径） */
    private BigDecimal adjustmentNet = BigDecimal.ZERO;

    /** 月结后客户应结金额 = 验收实收 + 调整净额 */
    private BigDecimal settleAmount = BigDecimal.ZERO;
}

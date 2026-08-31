package com.lin.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 原订单关联的下月调整单摘要 VO（蓝图 §2「月结调整追溯」）
 *
 * <p>t_month_adjustment 仅有「客户+结算月」粒度（无订单级外键），订单级摘要 =
 * 该客户订单归月（已提交验收单 accept_date 所在月，取最近一张）下的全部调整单；
 * 不改写原订单快照。未验收归月的订单 billMonth 为空、adjustments 为空列表。</p>
 *
 * @author dsh
 */
@Data
public class OrderAdjustmentSummaryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 订单归月（yyyy-MM，取最近已提交验收单 accept_date 所在月；未验收为 null） */
    private String billMonth;

    /** 该客户该结算月下的调整单列表（含草稿，草稿标注状态供区分） */
    private List<com.lin.distribution.domain.MonthAdjustment> adjustments;

    /** 应收调整合计（含草稿） */
    private BigDecimal receivableTotal;

    /** 采购成本调整合计（含草稿） */
    private BigDecimal costTotal;
}

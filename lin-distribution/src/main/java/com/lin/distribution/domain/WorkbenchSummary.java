package com.lin.distribution.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作台待办聚合结果
 *
 * @author dsh
 */
@Data
public class WorkbenchSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 待录/待确认订单数（DRAFT 草稿） */
    private Long draftOrders;

    /** 已确认订单数（S2-2.1 待办链首站：待生成采购/送货） */
    private Long confirmedOrders;

    /** 待生成采购单（明日配送的已确认订单数） */
    private Long pendingPurchase;

    /** 草稿采购单数（S2-2.1 待办链：批量采购阶段，待确认） */
    private Long purchaseDraft;

    /** 已确认未入库采购单数（S2-2.1/2.2 待办链：到货→待确认成本） */
    private Long purchasePendingCost;

    /** 待打印送货单数 */
    private Long pendingPrint;

    /** 已打印待送达登记数（S2-2.1 待办链：送达登记阶段） */
    private Long pendingMarkDelivered;

    /** 待验收送货单数（已送达未验收） */
    private Long pendingAcceptance;

    /** 待处理加退换数 */
    private Long pendingAdjust;
}

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

    /** 待生成采购单（明日配送的已确认订单数） */
    private Long pendingPurchase;

    /** 待打印送货单数 */
    private Long pendingPrint;

    /** 待验收送货单数（已送达未验收） */
    private Long pendingAcceptance;

    /** 待处理加退换数 */
    private Long pendingAdjust;
}

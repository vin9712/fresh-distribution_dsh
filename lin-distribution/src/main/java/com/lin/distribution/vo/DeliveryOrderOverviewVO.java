package com.lin.distribution.vo;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 送货单据页「当日全部客户总览」卡片行（D-071，2026-09-19）
 *
 * <p>D-055 送货单视图化后，送货单 = 已确认订单（{@code t_sale_order.status >= 1}）的视图，
 * 不再有物理送货单。故本 VO 以**订单**为口径按客户聚合：单数 / 点数 / 合计数量与金额 /
 * 各订单状态数（已确认/已配送/已验收/已结算）。</p>
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrderOverviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 客户ID */
    private Long customerId;

    /** 客户名（别名优先） */
    private String customerName;

    /** 配送日期 yyyy-MM-dd */
    private String deliveryDate;

    /** 订单数（status>=1 的去重订单） */
    private Integer orderCount;

    /** 配送点数（去重 delivery_point_id） */
    private Integer pointCount;

    /** 合计数量（Σ 明细 num） */
    private BigDecimal totalQuantity;

    /** 合计金额（Σ 明细 expect_amount） */
    private BigDecimal totalAmount;

    /** 已确认订单数 */
    private Integer confirmedCount;

    /** 已配送订单数 */
    private Integer deliveredCount;

    /** 已验收订单数 */
    private Integer acceptedCount;

    /** 已结算订单数 */
    private Integer settledCount;
}

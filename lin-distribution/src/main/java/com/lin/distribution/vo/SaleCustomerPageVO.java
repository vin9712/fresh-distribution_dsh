package com.lin.distribution.vo;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 销售订单页客户视角分组视图（D-064，《销售订单客户视角聚合设计》）
 *
 * <p>展示单元 = 客户 + 配送日期。一行 = 一个客户的一天，聚合该分组下的订单：
 * 张数 / 配送点数 / 各状态张数 / 合计金额。子行（订单编号 / 配送点 / 班次 / 状态 / 金额 / 操作）
 * 由前端展开时按 {@code customerId + deliveryDate} 复用 {@code /order/sale/list} 拉取。</p>
 *
 * <p>聚合必须<b>在后端分组分页</b>——前端分页会切断同一客户（与 D-043 送货单批次视图同理）。
 * 本查询故意<b>不复用</b> {@code selectSaleOrderVo}：其采购单号/送货单号/allocated 三个相关子查询
 * 在 {@code group by} 下会被逐行放大，故另建精简 SQL。</p>
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleCustomerPageVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 客户ID */
    private Long customerId;

    /** 客户名（别名优先） */
    private String customerName;

    /** 配送日期 yyyy-MM-dd */
    private String deliveryDate;

    /** 订单张数（分组内订单数） */
    private Integer orderCount;

    /** 配送点数（customer_dept_id 去重；含未指定配送点的订单为 1 个"空点"） */
    private Integer pointCount;

    /** 合计金额（Σ amount） */
    private BigDecimal totalAmount;

    /** 草稿数（status=0，待确认） */
    private Integer draftCount;

    /** 已确认数（status=1） */
    private Integer confirmedCount;

    /** 已配送数（status=2，待验收） */
    private Integer deliveredCount;

    /** 已验收数（status=3，待结算） */
    private Integer acceptedCount;

    /** 已结算数（status=4） */
    private Integer settledCount;
}

package com.lin.distribution.vo;

import lombok.Data;

import java.util.List;

/**
 * 「去验收」定位结果（S14 §6.1/§八：订单列表已配送行跳转验收页）
 *
 * <p>按来源订单反查其所在的有效送货单与验收单：
 * 有验收单 → 前端直接跳验收单；无验收单 → 前端带 deliveryId 引导创建验收草稿。</p>
 *
 * @author dsh
 */
@Data
public class AcceptanceByOrderVO {

    /** 来源销售订单ID（查询入参回显） */
    private Long orderId;

    /** 客户ID（AC-5 订单视角定位必返；历史回退路径可为空） */
    private Long customerId;

    /** 配送日期 yyyy-MM-dd（AC-5 订单视角定位必返，前端据此一键建草稿） */
    private String deliveryDate;

    /**
     * 命中的送货单ID（候选有效单中最新一张；补充单场景取最新补充单）
     * null = 该订单尚未进入任何有效送货单
     */
    private Long deliveryId;

    /** 命中的送货单号 */
    private String deliveryCode;

    /** 命中的送货单状态（0待打印 1已打印 2已送达 3已作废） */
    private Integer deliveryStatus;

    /**
     * 该订单参与的全部有效送货单ID（含补充单场景的多张单，按 id 升序）
     * 供前端提示"该订单分布在多张送货单"
     */
    private List<Long> deliveryIds;

    /** 是否已存在验收单 */
    private Boolean hasAcceptance;

    /** 命中的验收单ID（hasAcceptance=true 时有值） */
    private Long acceptanceId;

    /** 命中的验收单号 */
    private String acceptanceCode;

    /** 验收单状态（0草稿 1已提交） */
    private Integer acceptanceStatus;
}

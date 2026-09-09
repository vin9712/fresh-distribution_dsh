package com.lin.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 订单一键验收请求（OA，《订单页一键验收链路设计》§4.5）：
 * 建单（如无）→ 同步缺失行 → 应用实收覆盖（可选）→ 提交，事务内原子完成。
 * 全部行缺省实收=下单数量（一键验收语义：不做行级修改按订单数量金额整单确认）。
 *
 * @author dsh
 */
@Data
public class AcceptanceQuickAcceptDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 来源销售订单ID（acceptanceId 为空时据此定位/建单，必填） */
    private Long orderId;

    /** 已有草稿验收单ID（可选；不传则按 orderId 自动定位） */
    private Long acceptanceId;

    /** 验收日期（可选；缺省取订单配送日期） */
    private LocalDate acceptDate;

    /** 备注（可选） */
    private String remark;

    /** 实收覆盖行（可选；仅提交有差异的行，键=订单明细ID） */
    private List<Item> items;

    /**
     * 实收覆盖行（键=saleOrderDetailId：新建单场景行ID尚未生成，统一按订单明细定位）
     */
    @Data
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 订单明细ID（验收行通过 sale_order_detail_id 关联） */
        private Long saleOrderDetailId;

        /** 实收数量 */
        private BigDecimal actualQuantity;

        /** 差异原因（差异必填校验同 updateDraft：全部拒收/超收必填） */
        private String lossReason;
    }
}

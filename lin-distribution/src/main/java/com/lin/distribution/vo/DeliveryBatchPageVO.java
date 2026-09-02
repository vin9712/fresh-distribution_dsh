package com.lin.distribution.vo;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 送货单据页批次分组视图（D-043，《送货单矩阵总表与批次视图设计》§八）
 *
 * <p>管理单元 = 客户 + 配送日期（批次）。一行 = 一个批次，聚合该批次下的单据：
 * 张数 / 各状态张数 / 合计数量与金额 / 打印形态 / 待验收提醒最高级。</p>
 *
 * <p>聚合必须<b>在后端分组分页</b>（否则前端分页会切断同一批次）；本 VO 是分组后的主行，
 * 子行（单号/点/状态/打印次数/补充单/predecessor）由 {@code batchChildren} 携带，
 * 或前端展开时再按 batchId 拉取。</p>
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryBatchPageVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 批次ID（无批次的历史单可为 null） */
    private Long batchId;

    /** 客户ID */
    private Long customerId;

    /** 客户名（别名优先） */
    private String customerName;

    /** 配送日期 yyyy-MM-dd */
    private String deliveryDate;

    /** 组单范围快照 CUSTOMER_DATE / DELIVERY_POINT_DATE */
    private String scopeType;

    /** 打印形态 MATRIX / FLAT（取批次布局快照 printForm；无快照按 scopeType 推导） */
    private String printForm;

    /** 该批次单据数（原单 + 补充单，不含已作废） */
    private Integer docCount;

    /** 已打印数（status=1） */
    private Integer printedCount;

    /** 待打印数（status=0） */
    private Integer pendingCount;

    /** 已送达数（status=2） */
    private Integer deliveredCount;

    /** 已作废数（status=3） */
    private Integer voidedCount;

    /** 补充单数（doc_kind=1） */
    private Integer supplementCount;

    /** 配送点数（点单去重；跨点总单为 0/1，按 delivery_point_id 去重） */
    private Integer pointCount;

    /** 合计数量（Σ 明细 num） */
    private BigDecimal totalQuantity;

    /** 合计金额（Σ 明细 amount） */
    private BigDecimal totalAmount;

    /** 待验收提醒最高级（0无 / 1黄 / 2红；聚合到批次主行，D-043） */
    private Integer maxReminderLevel;

    /** 该批次下单据子行（供前端二层展开用；后端分组时一并带出） */
    private java.util.List<?> children;
}

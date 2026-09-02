package com.lin.distribution.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 送货单打印包（P2/D-050，《送货单矩阵总表与批次视图设计》§七）
 *
 * <p>一次打印动作的容器（批次级）：B 类客户 N 张点单一次输出——建包 → 包内任务清单 →
 * 汇总预览 → 队列连续输出 → 逐张回执。</p>
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPrintPackage implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 状态：待打印 */
    public static final int STATUS_PENDING = 0;
    /** 状态：打印中 */
    public static final int STATUS_PRINTING = 1;
    /** 状态：已完成 */
    public static final int STATUS_DONE = 2;
    /** 状态：已取消 */
    public static final int STATUS_CANCELLED = 3;

    private Long id;
    /** 配送批次ID（可空=历史单无批次） */
    private Long batchId;
    private Long customerId;
    private Date deliveryDate;
    /** 打印包编号 PKyyyyMMddNNN */
    private String packageNo;
    /** 介质：A4 / DOT_MATRIX（针式） */
    private String mediaType;
    private Integer status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private Date previewTime;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String remark;

    /** 客户名（联查展示用） */
    private String customerName;
    /** 包内任务列表（展示用） */
    private List<DeliveryPrintTask> tasks;
    /** 包内送货单合计数量/金额（展示用） */
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
}

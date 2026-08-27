package com.lin.distribution.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 退货单对象 t_return_order
 * （订单-送货-验收链路详细设计 §三 ⑤ / D-032/D-034：独立于验收单，不撤回历史验收）
 *
 * @author dsh
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class ReturnOrder extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 退货单号（THyyyyMMddNNN） */
    private String code;

    /** 客户ID */
    private Long customerId;

    /** 配送点ID（可空，整单级退货时可为空） */
    private Long customerDeptId;

    /** 原送货单ID */
    private Long deliveryId;

    /** 原验收单ID(退货单价来源) */
    private Long acceptanceId;

    /** 退货日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate returnDate;

    /** 合计退货金额(负项入对账) */
    private BigDecimal totalAmount;

    /** 状态：0草稿 1已提交(质检中) 2质检完成 3已完成 */
    private Integer status;

    /** 质检处理人 */
    private String inspectedBy;

    /** 质检时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date inspectedTime;

    /** 结算口径：0结算前当期冲销 1结算后下期冲销(提交时计算快照) */
    private Integer settleScope;

    /** 版本号 */
    private Integer version;

    /** 逻辑删除 */
    private Boolean isDeleted;

    /** 客户名称（列表展示用，查询时关联填充） */
    private String customerName;

    /** 配送点名称（列表展示用，查询时关联填充） */
    private String customerDeptName;

    /** 验收单编号（列表展示用，查询时关联填充） */
    private String acceptanceCode;
}

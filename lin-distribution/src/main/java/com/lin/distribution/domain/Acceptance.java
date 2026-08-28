package com.lin.distribution.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 验收单对象 acceptance（DESIGN.md §9：一单一验，验收总额为结算依据）
 *
 * @author dsh
 */
@Data
public class Acceptance extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 验收单号（YSyyyyMMddNNN） */
    @Excel(name = "验收单号")
    private String code;

    /** 送货单ID（唯一，一单一验） */
    @Excel(name = "送货单ID")
    private Long deliveryOrderId;

    /** 客户ID */
    @Excel(name = "客户ID")
    private Long customerId;

    /** 配送点ID */
    @Excel(name = "配送点ID")
    private Long deliveryPointId;

    /** 验收日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "验收日期", width = 30, dateFormat = "yyyy-MM-dd")
    private LocalDate acceptDate;

    /** 验收日期范围起（查询条件，非表字段，月结预览用） */
    private LocalDate beginAcceptDate;

    /** 验收日期范围止（查询条件，非表字段，月结预览用） */
    private LocalDate endAcceptDate;

    /** 验收总额（结算依据） */
    @Excel(name = "验收总额")
    private BigDecimal totalAmount;

    /** 状态：0草稿 1已提交 */
    @Excel(name = "状态", readConverterExp = "0=草稿,1=已提交")
    private Integer status;

    /** 撤回原因（S14：撤回后回填，作为已提交→草稿的审计线索） */
    private String revokeReason;

    /** 撤回人 */
    private String revokedBy;

    /** 撤回时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date revokedTime;

    /** 送货单编号（列表展示用，查询时关联填充） */
    private String deliveryCode;

    /** 客户名称（列表展示用，查询时关联填充） */
    private String customerName;

    /** 配送点名称（列表展示用，查询时关联填充） */
    private String customerDeptName;
}

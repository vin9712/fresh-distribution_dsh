package com.lin.distribution.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报表查询扁平行（验收单明细 + 客户/配送点/送货单关联字段）
 * 销售日报与客户对账单共用，由 ReportMapper 查询、ReportService 组装分组。
 *
 * @author dsh
 */
@Data
public class ReportRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 验收单ID */
    private Long acceptanceId;

    /** 验收单号 */
    private String acceptanceCode;

    /** 验收日期 */
    private LocalDate acceptDate;

    /** 验收单总额（结算依据） */
    private BigDecimal acceptanceTotalAmount;

    /** 送货单号 */
    private String deliveryCode;

    /** 配送日期（日报按此分组选日） */
    private LocalDate deliveryDate;

    /** 客户ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 配送点ID */
    private Long deliveryPointId;

    /** 配送点名称 */
    private String deliveryPointName;

    /** 商品名称快照 */
    private String productName;

    /** 规格快照 */
    private String productSpec;

    /** 单位快照 */
    private String productUnit;

    /** 实收数量 */
    private BigDecimal actualQuantity;

    /** 损耗数量（实收-送货，可为负） */
    private BigDecimal lossQuantity;

    /** 单价快照 */
    private BigDecimal unitPrice;

    /** 实收金额（实收×单价） */
    private BigDecimal actualAmount;

    /** 明细排序 */
    private Integer sort;
}

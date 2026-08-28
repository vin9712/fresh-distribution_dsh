package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 已确认采购单调整审计日志对象 purchase_modify_log（W0-2.5）
 *
 * <p>记录已确认采购单被直接修改数量/成本时的前后金额与明细快照，承载「操作日志 + 前后金额记录」，
 * 便于追溯「已确认采购纠错」与「报表重算」的变更依据。</p>
 *
 * @author dsh
 */
@Data
public class PurchaseModifyLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 采购单ID */
    @Excel(name = "采购单ID")
    private Long purchaseId;

    /** 采购单号 */
    @Excel(name = "采购单号")
    private String purchaseCode;

    /** 调整前采购总额 */
    @Excel(name = "调整前总额")
    private BigDecimal beforeAmount;

    /** 调整后采购总额 */
    @Excel(name = "调整后总额")
    private BigDecimal afterAmount;

    /** 调整前明细快照(JSON) */
    private String beforeItems;

    /** 调整后明细快照(JSON) */
    private String afterItems;

    /** 操作人 */
    @Excel(name = "操作人")
    private String operator;

    /** 操作时间 */
    @Excel(name = "操作时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date operateTime;

    /** 调整说明 */
    @Excel(name = "调整说明")
    private String remark;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("purchaseId", getPurchaseId())
            .append("purchaseCode", getPurchaseCode())
            .append("beforeAmount", getBeforeAmount())
            .append("afterAmount", getAfterAmount())
            .append("operator", getOperator())
            .append("operateTime", getOperateTime())
            .toString();
    }
}

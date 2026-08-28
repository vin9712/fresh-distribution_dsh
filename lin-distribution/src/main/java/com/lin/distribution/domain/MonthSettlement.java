package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.Date;

/**
 * 客户月度结算对象 t_month_settlement（蓝图 W0-3.1 按客户月结）
 *
 * <p>按「客户 + 结算月」锁定：月结后该客户该月的验收单、采购成本、下月调整单与退货单均冻结，
 * 纠错一律走《下月调整单》（W0-2.7），不改写原单。</p>
 *
 * @author dsh
 */
@Data
public class MonthSettlement implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 客户ID */
    @Excel(name = "客户ID")
    private Long customerId;

    /** 客户名称（列表展示，非查询列） */
    private String customerName;

    /** 结算月份（yyyy-MM） */
    @Excel(name = "结算月份")
    private String billMonth;

    /** 状态：0未结 1已结 */
    @Excel(name = "状态", readConverterExp = "0=未结,1=已结")
    private Integer status;

    /** 结算人 */
    @Excel(name = "结算人")
    private String settledBy;

    /** 结算时间 */
    @Excel(name = "结算时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date settledTime;

    /** 备注 */
    @Excel(name = "备注")
    private String remark;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("customerId", getCustomerId())
            .append("billMonth", getBillMonth())
            .append("status", getStatus())
            .toString();
    }
}

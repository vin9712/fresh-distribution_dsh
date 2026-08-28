package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 下月调整单对象 t_month_adjustment（蓝图 W0-2.7）
 *
 * <p>独立单号、草稿/已提交；可分别调整客户应收金额与采购成本（分项留痕），
 * 用于月结后纠错（不改写原订单快照，仅记录调整）。提交后立即参与客户对账与经营概览重算（W0-3）。</p>
 *
 * @author dsh
 */
@Data
public class MonthAdjustment implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 调整单号（TJyyyyMMddNNN，独立序列） */
    @Excel(name = "调整单号")
    private String code;

    /** 客户ID */
    @Excel(name = "客户ID")
    private Long customerId;

    /** 结算所属月份（yyyy-MM，客户对账与经营概览重算周期） */
    @Excel(name = "结算月份")
    private String billMonth;

    /** 应收金额调整（可正可负；正=调增应收，负=调减） */
    @Excel(name = "应收调整")
    private BigDecimal receivableAmount;

    /** 采购成本调整（可正可负；正=调增成本，负=调减） */
    @Excel(name = "成本调整")
    private BigDecimal purchaseCostAmount;

    /** 状态：0草稿 1已提交 */
    @Excel(name = "状态", readConverterExp = "0=草稿,1=已提交")
    private Integer status;

    /** 备注 */
    @Excel(name = "备注")
    private String remark;

    /** 逻辑删除（0正常 1删除） */
    private Integer isDeleted;

    /** 创建时间 */
    @Excel(name = "创建时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("code", getCode())
            .append("customerId", getCustomerId())
            .append("billMonth", getBillMonth())
            .append("receivableAmount", getReceivableAmount())
            .append("purchaseCostAmount", getPurchaseCostAmount())
            .append("status", getStatus())
            .toString();
    }
}

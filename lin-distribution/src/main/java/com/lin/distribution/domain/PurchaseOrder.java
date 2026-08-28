package com.lin.distribution.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 采购单对象 purchase_order
 *
 * @author dsh
 */
@Data
public class PurchaseOrder extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 采购单号（PCyyyyMMddNNN） */
    @Excel(name = "采购单号")
    private String code;

    /** 采购归属日期（=订单配送日期） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "采购日期", width = 30, dateFormat = "yyyy-MM-dd")
    private LocalDate orderDate;

    /** 来源类型：1自动生成 2手工创建 */
    @Excel(name = "来源类型", readConverterExp = "1=自动生成,2=手工创建")
    private Integer sourceType;

    /** 来源订单ID列表（JSON） */
    @Excel(name = "来源订单ID")
    private String sourceOrderIds;

    /** 供应商ID（可空，确认时后补） */
    @Excel(name = "供应商ID")
    private Long supplierId;

    /** 供应商名称（直填） */
    @Excel(name = "供应商名称")
    private String supplierName;

    /** 采购员 */
    @Excel(name = "采购员")
    private String purchaser;

    /** 采购总额 */
    @Excel(name = "采购总额")
    private BigDecimal totalAmount;

    /** 状态：0草稿 1已确认 2已入库 3已作废 */
    @Excel(name = "状态", readConverterExp = "0=草稿,1=已确认,2=已入库,3=已作废")
    private Integer status;

    /** 作废原因（W0-2.1：撤回级联空单自动作废记录“订单撤回”） */
    @Excel(name = "作废原因")
    private String voidReason;

    /** 作废人 */
    private String voidBy;

    /** 作废时间 */
    private Date voidTime;

    /** 采购明细（非表字段，新增/修改时携带） */
    @TableField(exist = false)
    private List<PurchaseItem> items;

    /** 采购日期范围起（查询条件，非表字段） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField(exist = false)
    private LocalDate beginOrderDate;

    /** 采购日期范围止（查询条件，非表字段） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField(exist = false)
    private LocalDate endOrderDate;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("code", getCode())
            .append("orderDate", getOrderDate())
            .append("sourceType", getSourceType())
            .append("sourceOrderIds", getSourceOrderIds())
            .append("supplierId", getSupplierId())
            .append("supplierName", getSupplierName())
            .append("totalAmount", getTotalAmount())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}

package com.lin.distribution.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.*;

/**
 * 销售订单对象 t_sale_order
 *
 * @author lin
 * @date 2024-11-23
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class SaleOrder extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 客户ID
     */
    @Excel(name = "客户ID")
    private Long customerId;

    /**
     * 客户部门ID
     */
    @Excel(name = "客户部门ID")
    private Long customerDeptId;

    /**
     * 订单编号
     */
    @Excel(name = "订单编号")
    private String code;

    /**
     * 订单来源：1后台下单,2线上下单
     */
    @Excel(name = "订单来源：1后台下单,2线上下单")
    private Integer source;

    /**
     * 订单类型：1正常订单,2加单
     */
    @Excel(name = "订单类型：1正常订单,2加单")
    private Integer type;

    /**
     * 总金额
     */
    @Excel(name = "总金额")
    private BigDecimal amount;

    /**
     * 状态：0制单,1审核,2送货,3验收,4完成
     */
    @Excel(name = "状态：0制单,1审核,2送货,3验收,4完成")
    private Integer status;

    /**
     * 预计配送日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "预计配送日期", width = 30, dateFormat = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    /**
     * 逻辑删除
     */
    private Boolean isDeleted;

    /**
     * 版本号
     */
    @Version
    private Integer version;

}

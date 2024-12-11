package com.lin.distribution.domain;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.*;

/**
 * 送货单据对象 t_delivery_order
 *
 * @author lin
 * @date 2024-12-11
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class DeliveryOrder extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 订单ID
     */
    @Excel(name = "订单ID")
    private Long orderId;

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
    private String orderCode;

    /**
     * 送货单状态：0制单,1送货,2完成
     */
    @Excel(name = "送货单状态：0制单,1送货,2完成")
    private Integer status;

    /**
     * 配送日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "配送日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date deliveryDate;

    /**
     * 逻辑删除
     */
    private Boolean isDeleted;

    /**
     * 版本号
     */
    private Integer version;

}
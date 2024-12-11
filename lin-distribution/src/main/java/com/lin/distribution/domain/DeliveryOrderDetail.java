package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.*;

/**
 * 送货单详情对象 t_delivery_order_detail
 *
 * @author lin
 * @date 2024-12-11
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class DeliveryOrderDetail extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 送货单ID
     */
    @Excel(name = "送货单ID")
    private Long deliveryId;

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
     * 打印状态
     */
    @Excel(name = "打印状态")
    private Boolean isPrint;

    /**
     * 逻辑删除
     */
    private Boolean isDeleted;

    /**
     * 版本号
     */
    private Integer version;

}

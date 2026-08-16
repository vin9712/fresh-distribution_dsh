package com.lin.distribution.domain;

import com.baomidou.mybatisplus.annotation.Version;
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
     * 配送点ID
     */
    @Excel(name = "配送点ID")
    private Long customerDeptId;

    /**
     * 订单编号
     */
    @Excel(name = "订单编号")
    private String orderCode;

    /**
     * SKU（临时商品可空）
     */
    private Long skuId;

    /**
     * 商品名称快照
     */
    @Excel(name = "商品名称")
    private String productName;

    /**
     * 单位快照
     */
    private String productUnit;

    /**
     * 规格快照
     */
    private String productSpec;

    /**
     * 送货数量
     */
    @Excel(name = "送货数量")
    private java.math.BigDecimal num;

    /**
     * 单价快照
     */
    @Excel(name = "单价")
    private java.math.BigDecimal price;

    /**
     * 小计（num*price）
     */
    @Excel(name = "小计")
    private java.math.BigDecimal amount;

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
    @Version
    private Integer version;

}

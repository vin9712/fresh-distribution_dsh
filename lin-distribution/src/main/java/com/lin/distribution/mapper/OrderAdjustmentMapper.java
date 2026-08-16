package com.lin.distribution.mapper;

import com.lin.distribution.domain.OrderAdjustment;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单加退换调整 Mapper
 *
 * @author dsh
 */
public interface OrderAdjustmentMapper {

    /** 新增订单调整 */
    int insertOrderAdjustment(OrderAdjustment orderAdjustment);

    /** 按订单ID查询调整列表 */
    List<OrderAdjustment> selectOrderAdjustmentByOrderId(Long orderId);

    /** 查询订单调整详情 */
    OrderAdjustment selectOrderAdjustmentById(Long id);

    /**
     * 调整后更新订单：置 adjust_flag=1 并重算金额
     * （t_sale_order 现有 SaleOrder 域/mapper 未含 adjust_flag，故在此直接更新）
     */
    int updateOrderAdjustFlagAndAmount(@Param("orderId") Long orderId, @Param("amount") BigDecimal amount);
}

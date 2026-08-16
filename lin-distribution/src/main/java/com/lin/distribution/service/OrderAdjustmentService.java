package com.lin.distribution.service;

import com.lin.distribution.domain.OrderAdjustment;
import com.lin.distribution.dto.OrderAdjustmentCreateDTO;

import java.util.List;

/**
 * 订单加退换调整Service接口
 *
 * @author dsh
 */
public interface OrderAdjustmentService {

    /** 创建订单调整（配送后调整） */
    void createAdjustment(OrderAdjustmentCreateDTO dto);

    /** 按订单ID查询调整列表 */
    List<OrderAdjustment> selectByOrderId(Long orderId);

    /** 查询调整详情 */
    OrderAdjustment selectById(Long id);
}

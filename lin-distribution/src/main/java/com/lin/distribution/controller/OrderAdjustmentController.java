package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.dto.OrderAdjustmentCreateDTO;
import com.lin.distribution.service.OrderAdjustmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 订单加退换调整Controller
 *
 * @author dsh
 */
@Tag(name = "订单加退换调整")
@RestController
@RequestMapping("/order/adjustment")
public class OrderAdjustmentController extends BaseController {
    @Autowired
    private OrderAdjustmentService orderAdjustmentService;

    /**
     * 创建订单调整
     */
    @Operation(summary = "创建订单调整")
    @PreAuthorize("@ss.hasPermi('order:sale:adjust')")
    @Log(title = "订单调整", businessType = BusinessType.UPDATE)
    @PostMapping
    public AjaxResult add(@RequestBody OrderAdjustmentCreateDTO dto) {
        orderAdjustmentService.createAdjustment(dto);
        return success();
    }

    /**
     * 按订单ID查询调整列表
     */
    @Operation(summary = "按订单ID查询调整列表")
    @PreAuthorize("@ss.hasPermi('order:sale:list')")
    @GetMapping("/order/{orderId}")
    public AjaxResult listByOrder(@PathVariable("orderId") Long orderId) {
        return success(orderAdjustmentService.selectByOrderId(orderId));
    }

    /**
     * 查询调整详情
     */
    @Operation(summary = "查询调整详情")
    @PreAuthorize("@ss.hasPermi('order:sale:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(orderAdjustmentService.selectById(id));
    }
}

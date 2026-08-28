package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.PurchaseOrder;
import com.lin.distribution.dto.PurchaseByOrdersDTO;
import com.lin.distribution.dto.PurchaseGenerateDTO;
import com.lin.distribution.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购单Controller
 *
 * @author dsh
 */
@Tag(name = "采购单管理")
@RestController
@RequestMapping("/purchase")
public class PurchaseOrderController extends BaseController {
    @Autowired
    private PurchaseOrderService purchaseOrderService;

    /**
     * 查询采购单列表
     */
    @Operation(summary = "查询采购单列表")
    @PreAuthorize("@ss.hasPermi('purchase:list')")
    @GetMapping("/list")
    public AjaxResult list(PurchaseOrder purchaseOrder) {
        List<PurchaseOrder> list = purchaseOrderService.selectPurchaseOrderList(purchaseOrder);
        return success(list);
    }

    /**
     * 获取采购单详细信息
     */
    @Operation(summary = "获取采购单详细信息")
    @PreAuthorize("@ss.hasPermi('purchase:list')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(purchaseOrderService.selectPurchaseOrderById(id));
    }

    /**
     * 获取采购单明细列表
     */
    @Operation(summary = "获取采购单明细列表")
    @PreAuthorize("@ss.hasPermi('purchase:list')")
    @GetMapping(value = "/{id}/items")
    public AjaxResult getItems(@PathVariable("id") Long id) {
        return success(purchaseOrderService.selectPurchaseItemListByPurchaseId(id));
    }

    /**
     * 按配送日期自动生成采购单
     */
    @Operation(summary = "按配送日期自动生成采购单")
    @PreAuthorize("@ss.hasPermi('purchase:add')")
    @Log(title = "采购单", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public AjaxResult generate(@RequestBody @Validated PurchaseGenerateDTO dto) {
        return success(purchaseOrderService.generateByOrderDate(dto));
    }

    /**
     * 按勾选订单自动生成采购单（销售订单列表页抽屉）
     */
    @Operation(summary = "按勾选订单自动生成采购单")
    @PreAuthorize("@ss.hasPermi('purchase:add')")
    @Log(title = "采购单", businessType = BusinessType.INSERT)
    @PostMapping("/generate-by-orders")
    public AjaxResult generateByOrders(@RequestBody @Validated PurchaseByOrdersDTO dto) {
        return success(purchaseOrderService.generateByOrderIds(dto));
    }

    /**
     * 手工创建采购单
     */
    @Operation(summary = "手工创建采购单")
    @PreAuthorize("@ss.hasPermi('purchase:add')")
    @Log(title = "采购单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody PurchaseOrder purchaseOrder) {
        return toAjax(purchaseOrderService.createPurchase(purchaseOrder));
    }

    /**
     * 修改采购单
     */
    @Operation(summary = "修改采购单")
    @PreAuthorize("@ss.hasPermi('purchase:edit')")
    @Log(title = "采购单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PurchaseOrder purchaseOrder) {
        return toAjax(purchaseOrderService.updatePurchase(purchaseOrder));
    }

    /**
     * 确认采购单
     */
    @Operation(summary = "确认采购单")
    @PreAuthorize("@ss.hasPermi('purchase:edit')")
    @Log(title = "采购单", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/confirm")
    public AjaxResult confirm(@PathVariable("id") Long id) {
        return toAjax(purchaseOrderService.confirm(id));
    }

    /**
     * 采购单入库
     */
    @Operation(summary = "采购单入库")
    @PreAuthorize("@ss.hasPermi('purchase:edit')")
    @Log(title = "采购单", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/stockIn")
    public AjaxResult stockIn(@PathVariable("id") Long id) {
        return toAjax(purchaseOrderService.stockIn(id));
    }

    /**
     * 已确认采购单直接调整数量/成本（W0-2.5「已确认采购纠错」）：记录前后金额审计日志
     */
    @Operation(summary = "已确认采购单调整数量/成本")
    @PreAuthorize("@ss.hasPermi('purchase:edit')")
    @Log(title = "采购单", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/adjust")
    public AjaxResult adjust(@PathVariable("id") Long id, @RequestBody PurchaseOrder purchaseOrder) {
        purchaseOrder.setId(id);
        return success(purchaseOrderService.adjustConfirmedPurchase(purchaseOrder));
    }

    /**
     * 查询采购单调整审计日志列表（W0-2.5）
     */
    @Operation(summary = "查询采购单调整审计日志")
    @PreAuthorize("@ss.hasPermi('purchase:list')")
    @GetMapping("/{id}/modify-logs")
    public AjaxResult modifyLogs(@PathVariable("id") Long id) {
        return success(purchaseOrderService.selectModifyLogsByPurchaseId(id));
    }

    /**
     * 删除采购单
     */
    @Operation(summary = "删除采购单")
    @PreAuthorize("@ss.hasPermi('purchase:remove')")
    @Log(title = "采购单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(purchaseOrderService.deletePurchaseOrderByIds(ids));
    }
}

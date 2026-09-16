package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.PurchaseOrder;
import com.lin.distribution.dto.PurchaseBatchDTO;
import com.lin.distribution.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 采购单Controller
 *
 * <p>D-056~D-063：日应采汇总（订单视图）+ 分批成本录入（行=进货批次）。</p>
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
        return success(purchaseOrderService.selectPurchaseOrderList(purchaseOrder));
    }

    /**
     * 查询当日应采汇总（应采/已采/待采/批次数/加权均价/金额 + 批次明细）
     */
    @Operation(summary = "查询当日应采汇总")
    @PreAuthorize("@ss.hasPermi('purchase:list')")
    @GetMapping("/day-summary")
    public AjaxResult daySummary(@RequestParam("orderDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDate) {
        return success(purchaseOrderService.daySummary(orderDate));
    }

    /**
     * 采购新增商品：按关键词检索商品库 SKU（供采购录入选品，权限走 purchase:list）
     */
    @Operation(summary = "采购新增商品-SKU 检索")
    @PreAuthorize("@ss.hasPermi('purchase:list')")
    @GetMapping("/sku-options")
    public AjaxResult skuOptions(@RequestParam(value = "name", required = false) String name) {
        return success(purchaseOrderService.searchSkuOptions(name));
    }

    /**
     * 取或惰性创建当日采购单（有写副作用，故用 POST 保证不被预取/爬虫误触）
     */
    @Operation(summary = "取或创建当日采购单")
    @PreAuthorize("@ss.hasPermi('purchase:add')")
    @Log(title = "采购单-建当日单", businessType = BusinessType.INSERT)
    @PostMapping("/day-order")
    public AjaxResult dayOrder(@RequestParam("orderDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDate) {
        return success(purchaseOrderService.getOrCreateDayPurchase(orderDate));
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
     * 获取采购单批次明细列表
     */
    @Operation(summary = "获取采购单批次明细列表")
    @PreAuthorize("@ss.hasPermi('purchase:list')")
    @GetMapping(value = "/{id}/items")
    public AjaxResult getItems(@PathVariable("id") Long id) {
        return success(purchaseOrderService.selectPurchaseItemListByPurchaseId(id));
    }

    /**
     * 追加一个进货批次（商品必须命中当日应采清单）
     */
    @Operation(summary = "追加采购批次")
    @PreAuthorize("@ss.hasPermi('purchase:add')")
    @Log(title = "采购单-批次录入", businessType = BusinessType.INSERT)
    @PostMapping("/{id}/batch")
    public AjaxResult addBatch(@PathVariable("id") Long id, @RequestBody @Valid PurchaseBatchDTO dto) {
        return success(purchaseOrderService.addBatch(id, dto));
    }

    /**
     * 批量追加进货批次（任一行非法整体回滚）
     */
    @Operation(summary = "批量追加采购批次")
    @PreAuthorize("@ss.hasPermi('purchase:add')")
    @Log(title = "采购单-批量录入", businessType = BusinessType.INSERT)
    @PostMapping("/{id}/batch-bulk")
    public AjaxResult addBatchBulk(@PathVariable("id") Long id, @RequestBody List<PurchaseBatchDTO> items) {
        return success(purchaseOrderService.addBatchBulk(id, items));
    }

    /**
     * 修改采购批次（仅草稿）
     */
    @Operation(summary = "修改采购批次")
    @PreAuthorize("@ss.hasPermi('purchase:edit')")
    @Log(title = "采购单-批次修改", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/batch/{itemId}")
    public AjaxResult updateBatch(@PathVariable("id") Long id, @PathVariable("itemId") Long itemId,
                                  @RequestBody @Valid PurchaseBatchDTO dto) {
        return toAjax(purchaseOrderService.updateBatch(id, itemId, dto));
    }

    /**
     * 删除采购批次（仅草稿）
     */
    @Operation(summary = "删除采购批次")
    @PreAuthorize("@ss.hasPermi('purchase:edit')")
    @Log(title = "采购单-批次删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}/batch/{itemId}")
    public AjaxResult deleteBatch(@PathVariable("id") Long id, @PathVariable("itemId") Long itemId) {
        return toAjax(purchaseOrderService.deleteBatch(id, itemId));
    }

    /**
     * 修改采购单单头（默认供应商/采购员/备注）
     */
    @Operation(summary = "修改采购单单头")
    @PreAuthorize("@ss.hasPermi('purchase:edit')")
    @Log(title = "采购单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PurchaseOrder purchaseOrder) {
        return toAjax(purchaseOrderService.updatePurchaseHeader(purchaseOrder));
    }

    /**
     * 确认采购单（草稿→已确认，锁定批次增删）
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
     * 批量入库（S2-2.2 批量确认成本）：仅已确认采购单，任一非法整体回滚
     */
    @Operation(summary = "采购单批量入库")
    @PreAuthorize("@ss.hasPermi('purchase:edit')")
    @Log(title = "采购单", businessType = BusinessType.UPDATE)
    @PutMapping("/batch-stock-in")
    public AjaxResult batchStockIn(@RequestBody Long[] ids) {
        return toAjax(purchaseOrderService.batchStockIn(ids));
    }

    /**
     * 供应商补录（S2-2.2）：草稿/已确认采购单补录供应商与采购员
     */
    @Operation(summary = "采购单供应商补录")
    @PreAuthorize("@ss.hasPermi('purchase:edit')")
    @Log(title = "采购单-供应商补录", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/supplier")
    public AjaxResult backfillSupplier(@PathVariable("id") Long id,
                                       @RequestBody PurchaseOrder body) {
        return toAjax(purchaseOrderService.backfillSupplier(id,
                body.getSupplierId(), body.getSupplierName(), body.getPurchaser()));
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

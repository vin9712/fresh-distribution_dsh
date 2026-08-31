package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.MonthAdjustment;
import com.lin.distribution.service.MonthAdjustmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 下月调整单Controller（蓝图 W0-2.7）
 *
 * @author dsh
 */
@Tag(name = "下月调整单")
@RestController
@RequestMapping("/month-adjustment")
public class MonthAdjustmentController extends BaseController {
    @Autowired
    private MonthAdjustmentService monthAdjustmentService;

    /**
     * 查询下月调整单列表
     */
    @Operation(summary = "查询下月调整单列表")
    @PreAuthorize("@ss.hasPermi('monthAdjustment:list')")
    @GetMapping("/list")
    public AjaxResult list(MonthAdjustment query) {
        return success(monthAdjustmentService.selectList(query));
    }

    /**
     * 获取下月调整单详细信息
     */
    @Operation(summary = "获取下月调整单详细信息")
    @PreAuthorize("@ss.hasPermi('monthAdjustment:list')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(monthAdjustmentService.selectById(id));
    }

    /**
     * 新增下月调整单（草稿）
     */
    @Operation(summary = "新增下月调整单")
    @PreAuthorize("@ss.hasPermi('monthAdjustment:add')")
    @Log(title = "下月调整单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody MonthAdjustment adjustment) {
        return success(monthAdjustmentService.create(adjustment));
    }

    /**
     * 修改下月调整单（仅草稿）
     */
    @Operation(summary = "修改下月调整单")
    @PreAuthorize("@ss.hasPermi('monthAdjustment:edit')")
    @Log(title = "下月调整单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody MonthAdjustment adjustment) {
        return toAjax(monthAdjustmentService.update(adjustment));
    }

    /**
     * 提交下月调整单（草稿→已提交）
     */
    @Operation(summary = "提交下月调整单")
    @PreAuthorize("@ss.hasPermi('monthAdjustment:edit')")
    @Log(title = "下月调整单", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/submit")
    public AjaxResult submit(@PathVariable("id") Long id) {
        return toAjax(monthAdjustmentService.submit(id));
    }

    /**
     * 删除下月调整单（仅草稿，逻辑删除）
     */
    @Operation(summary = "删除下月调整单")
    @PreAuthorize("@ss.hasPermi('monthAdjustment:remove')")
    @Log(title = "下月调整单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {
        return toAjax(monthAdjustmentService.delete(id));
    }

    /**
     * 原订单关联摘要（蓝图 §2「月结调整追溯」）：客户+结算月粒度，
     * 返回订单归月（最近已提交验收单 accept_date 所在月）下该客户全部调整单与合计
     */
    @Operation(summary = "原订单关联调整摘要")
    @PreAuthorize("@ss.hasAnyPermi('monthAdjustment:list,order:sale:query')")
    @GetMapping("/by-order/{saleOrderId}")
    public AjaxResult byOrder(@PathVariable("saleOrderId") Long saleOrderId) {
        return success(monthAdjustmentService.selectBySaleOrderId(saleOrderId));
    }
}

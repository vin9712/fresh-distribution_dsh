package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.core.page.TableDataInfo;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.ReturnItem;
import com.lin.distribution.domain.ReturnOrder;
import com.lin.distribution.dto.ReturnInspectDTO;
import com.lin.distribution.dto.ReturnOrderSaveDTO;
import com.lin.distribution.service.ReturnOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 退货单Controller（S14/T6，D-032/D-034：验收后真实退货独立单据，
 * 状态机 0草稿→1已提交(质检中)→2质检完成；3已完成由结算模块落）
 *
 * @author dsh
 */
@Tag(name = "退货单管理")
@RestController
@RequestMapping("/order/return")
public class ReturnOrderController extends BaseController {
    @Autowired
    private ReturnOrderService returnOrderService;

    /**
     * 分页查询退货单列表（支持按 settle_scope 过滤）
     */
    @Operation(summary = "分页查询退货单列表")
    @PreAuthorize("@ss.hasPermi('return:list')")
    @GetMapping("/page")
    public TableDataInfo page(ReturnOrder returnOrder) {
        startPage();
        List<ReturnOrder> list = returnOrderService.selectReturnOrderList(returnOrder);
        return getDataTable(list);
    }

    /**
     * 查询退货单列表
     */
    @Operation(summary = "查询退货单列表")
    @PreAuthorize("@ss.hasPermi('return:list')")
    @GetMapping("/list")
    public AjaxResult list(ReturnOrder returnOrder) {
        return success(returnOrderService.selectReturnOrderList(returnOrder));
    }

    /**
     * 获取退货单详情
     */
    @Operation(summary = "获取退货单详情")
    @PreAuthorize("@ss.hasPermi('return:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(returnOrderService.selectReturnOrderById(id));
    }

    /**
     * 获取退货单明细
     */
    @Operation(summary = "获取退货单明细")
    @PreAuthorize("@ss.hasPermi('return:query')")
    @GetMapping("/{id}/items")
    public AjaxResult items(@PathVariable("id") Long id) {
        List<ReturnItem> items = returnOrderService.selectItemsByReturnId(id);
        return success(Map.of("returnId", id, "items", items));
    }

    /**
     * 新增退货单草稿
     */
    @Operation(summary = "新增退货单草稿")
    @PreAuthorize("@ss.hasPermi('return:add')")
    @Log(title = "退货单新增", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ReturnOrderSaveDTO dto) {
        return success(returnOrderService.create(dto));
    }

    /**
     * 修改退货单草稿
     */
    @Operation(summary = "修改退货单草稿")
    @PreAuthorize("@ss.hasPermi('return:edit')")
    @Log(title = "退货单修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ReturnOrderSaveDTO dto) {
        return success(returnOrderService.updateDraft(dto));
    }

    /**
     * 提交退货单（草稿→已提交质检中，settle_scope 快照）
     */
    @Operation(summary = "提交退货单")
    @PreAuthorize("@ss.hasPermi('return:submit')")
    @Log(title = "退货单提交", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/submit")
    public AjaxResult submit(@PathVariable("id") Long id) {
        return success(returnOrderService.submit(id));
    }

    /**
     * 质检（已提交→质检完成，逐行记质检结论）
     */
    @Operation(summary = "退货单质检")
    @PreAuthorize("@ss.hasPermi('return:inspect')")
    @Log(title = "退货单质检", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/inspect")
    public AjaxResult inspect(@PathVariable("id") Long id, @Validated @RequestBody ReturnInspectDTO dto) {
        return success(returnOrderService.inspect(id, dto));
    }

    /**
     * 删除退货单（仅草稿，逻辑删除）
     */
    @Operation(summary = "删除退货单")
    @PreAuthorize("@ss.hasPermi('return:remove')")
    @Log(title = "退货单删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(returnOrderService.deleteByIds(ids));
    }
}

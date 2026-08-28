package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.MonthSettlement;
import com.lin.distribution.vo.MonthSettlementPreviewVO;
import com.lin.distribution.service.MonthSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 客户月度结算Controller（蓝图 W0-3.1 按客户月结）
 *
 * @author dsh
 */
@Tag(name = "客户月度结算")
@RestController
@RequestMapping("/month-settlement")
public class MonthSettlementController extends BaseController {
    @Autowired
    private MonthSettlementService monthSettlementService;

    /**
     * 查询月度结算记录列表
     */
    @Operation(summary = "查询月度结算记录列表")
    @PreAuthorize("@ss.hasPermi('monthSettlement:list')")
    @GetMapping("/list")
    public AjaxResult list(MonthSettlement query) {
        return success(monthSettlementService.selectList(query));
    }

    /**
     * 月结前预览该客户该月验收单与下月调整单及结算汇总
     */
    @Operation(summary = "月结预览")
    @PreAuthorize("@ss.hasPermi('monthSettlement:list')")
    @GetMapping("/preview")
    public AjaxResult preview(@RequestParam Long customerId, @RequestParam String billMonth) {
        return success(monthSettlementService.preview(customerId, billMonth));
    }

    /**
     * 执行客户月结（冻结该客户该月验收/采购成本/调整单/退货单）
     */
    @Operation(summary = "执行客户月结")
    @PreAuthorize("@ss.hasPermi('monthSettlement:settle')")
    @Log(title = "客户月结", businessType = BusinessType.UPDATE)
    @PostMapping("/settle")
    public AjaxResult settle(@RequestBody MonthSettlement request) {
        return success(monthSettlementService.settle(request.getCustomerId(), request.getBillMonth(), request.getRemark()));
    }

    /**
     * 判断是否已月结（冻结校验）
     */
    @Operation(summary = "判断是否已月结")
    @PreAuthorize("@ss.hasPermi('monthSettlement:list')")
    @GetMapping("/check")
    public AjaxResult check(@RequestParam Long customerId, @RequestParam String billMonth) {
        return success(monthSettlementService.isSettled(customerId, billMonth) ? "yes" : "no");
    }

    /**
     * 查询某客户某月结算记录
     */
    @Operation(summary = "查询某客户某月结算记录")
    @PreAuthorize("@ss.hasPermi('monthSettlement:list')")
    @GetMapping("/get")
    public AjaxResult get(@RequestParam Long customerId, @RequestParam String billMonth) {
        return success(monthSettlementService.getByCustomerAndMonth(customerId, billMonth));
    }

    /**
     * 导出该客户该月验收单（月结线下核对，统一模板）
     */
    @Operation(summary = "导出月结验收单")
    @PreAuthorize("@ss.hasPermi('monthSettlement:list')")
    @Log(title = "客户月结", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, @RequestParam Long customerId, @RequestParam String billMonth)
            throws Exception {
        MonthSettlementPreviewVO vo = monthSettlementService.preview(customerId, billMonth);
        ExcelUtil<Acceptance> util = new ExcelUtil<>(Acceptance.class);
        util.exportExcel(response, vo.getAcceptances(), "客户月结验收单-" + billMonth);
    }
}

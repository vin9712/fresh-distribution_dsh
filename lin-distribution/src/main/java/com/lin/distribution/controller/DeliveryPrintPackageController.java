package com.lin.distribution.controller;

import java.time.LocalDate;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.distribution.service.DeliveryPrintPackageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 送货单打印包接口（P2/D-050《送货单矩阵总表与批次视图设计》§七/§九）
 *
 * <p>B 类客户 N 张点单一次输出：建包 → 包内任务清单 → 汇总预览 → 队列连续输出 → 逐张回执。</p>
 *
 * @author dsh
 */
@Tag(name = "送货单打印包")
@RestController
@RequestMapping("/order/delivery/print-package")
public class DeliveryPrintPackageController extends BaseController {

    private final DeliveryPrintPackageService printPackageService;

    public DeliveryPrintPackageController(DeliveryPrintPackageService printPackageService) {
        this.printPackageService = printPackageService;
    }

    /**
     * 建打印包（按 客户+配送日期；已有未完成包则复用）
     */
    @Operation(summary = "建打印包")
    @PreAuthorize("@ss.hasAnyPermi('order:delivery:print,order:delivery:list')")
    @PostMapping
    public AjaxResult create(@RequestBody Map<String, Object> body) {
        Long customerId = toLong(body.get("customerId"));
        LocalDate deliveryDate = toDate(body.get("deliveryDate"));
        return success(printPackageService.createPackage(customerId, deliveryDate));
    }

    /**
     * 查打印包（含任务清单与合计）
     */
    @Operation(summary = "查打印包")
    @PreAuthorize("@ss.hasAnyPermi('order:delivery:print,order:delivery:list')")
    @GetMapping("/{packageId}")
    public AjaxResult get(@PathVariable("packageId") Long packageId) {
        return success(printPackageService.getPackage(packageId));
    }

    /**
     * 查批次当日打印包列表
     */
    @Operation(summary = "打印包列表")
    @PreAuthorize("@ss.hasAnyPermi('order:delivery:print,order:delivery:list')")
    @GetMapping("/list")
    public AjaxResult list(@org.springframework.web.bind.annotation.RequestParam(required = false) Long customerId,
                           @org.springframework.web.bind.annotation.RequestParam(required = false) String deliveryDate) {
        LocalDate date = deliveryDate == null || deliveryDate.isBlank() ? null : LocalDate.parse(deliveryDate);
        return success(printPackageService.listPackages(customerId, date));
    }

    /**
     * 汇总预览（标记包内全部待打任务为已预览，记录预览时间留痕）
     */
    @Operation(summary = "汇总预览")
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @PostMapping("/{packageId}/preview")
    public AjaxResult preview(@PathVariable("packageId") Long packageId) {
        return success(printPackageService.previewPackage(packageId));
    }

    /**
     * 开始打印（包状态 → 打印中；任务队列由前端按 seq_no 逐张执行）
     */
    @Operation(summary = "开始打印")
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @PostMapping("/{packageId}/start")
    public AjaxResult start(@PathVariable("packageId") Long packageId) {
        return success(printPackageService.startPackage(packageId));
    }

    /**
     * 单张回执（成功才计次/推进状态；失败不计数）
     */
    @Operation(summary = "单张回执")
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @PostMapping("/task/{taskId}/receipt")
    public AjaxResult receipt(@PathVariable("taskId") Long taskId, @RequestBody Map<String, Object> body) {
        boolean success = body == null || !Boolean.FALSE.equals(body.get("success"));
        String errorMsg = body == null ? null : body.get("errorMsg") == null ? null : String.valueOf(body.get("errorMsg"));
        return success(printPackageService.receipt(taskId, success, errorMsg));
    }

    /**
     * 逐张改模板/份数（仅本次生效）
     */
    @Operation(summary = "逐张改模板份数")
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @PutMapping("/task/{taskId}")
    public AjaxResult updateTask(@PathVariable("taskId") Long taskId, @RequestBody Map<String, Object> body) {
        Long templateId = body == null ? null : toLong(body.get("templateId"));
        Integer copies = body == null || body.get("copies") == null ? null : ((Number) body.get("copies")).intValue();
        return success(printPackageService.updateTask(taskId, templateId, copies));
    }

    private Long toLong(Object value) {
        if (value == null || String.valueOf(value).isBlank() || "null".equalsIgnoreCase(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate toDate(Object value) {
        if (value == null || String.valueOf(value).isBlank() || "null".equalsIgnoreCase(String.valueOf(value))) {
            return null;
        }
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }
}

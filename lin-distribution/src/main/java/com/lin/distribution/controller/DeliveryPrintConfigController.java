package com.lin.distribution.controller;

import java.util.List;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.DeliveryPrintConfigVersion;
import com.lin.distribution.dto.DeliveryPrintConfigDTO;
import com.lin.distribution.service.DeliveryPrintConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 送货单打印拆分配置Controller（W0-2.2）
 *
 * @author dsh
 */
@Tag(name = "送货单打印拆分配置")
@RestController
@RequestMapping("/order/delivery")
public class DeliveryPrintConfigController extends BaseController {

    @Autowired
    private DeliveryPrintConfigService deliveryPrintConfigService;

    /**
     * 解析送货单当前打印配置（无配置时按批次策略推导默认值 + 自动生成结构）
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @GetMapping("/{deliveryOrderId}/print-config")
    public AjaxResult config(@PathVariable("deliveryOrderId") Long deliveryOrderId) {
        return success(deliveryPrintConfigService.resolveConfig(deliveryOrderId));
    }

    /**
     * 保存打印拆分配置（仅未打印 PENDING 单可改；每次保存追加版本记录）
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @Log(title = "送货单打印拆分配置", businessType = BusinessType.UPDATE)
    @PutMapping("/{deliveryOrderId}/print-config")
    public AjaxResult save(@PathVariable("deliveryOrderId") Long deliveryOrderId,
                           @RequestBody DeliveryPrintConfigDTO dto) {
        return success(deliveryPrintConfigService.saveConfig(deliveryOrderId, dto));
    }

    /**
     * 查询打印配置版本记录（最新在前）
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @GetMapping("/{deliveryOrderId}/print-config/versions")
    public AjaxResult versions(@PathVariable("deliveryOrderId") Long deliveryOrderId) {
        List<DeliveryPrintConfigVersion> versions = deliveryPrintConfigService.listVersions(deliveryOrderId);
        return success(versions);
    }

    /**
     * 恢复自动生成结构（仅未打印 PENDING 单；追加版本记录）
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @Log(title = "送货单打印拆分配置", businessType = BusinessType.UPDATE)
    @PostMapping("/{deliveryOrderId}/print-config/restore")
    public AjaxResult restore(@PathVariable("deliveryOrderId") Long deliveryOrderId) {
        return success(deliveryPrintConfigService.restoreAutoStructure(deliveryOrderId));
    }
}

package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.DeliverySkuOverride;
import com.lin.distribution.service.DeliverySkuOverrideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 配送点商品覆盖Controller（delivery_sku_override，替代原配送点报价）
 *
 * @author dsh
 */
@Tag(name = "配送点商品覆盖管理")
@RestController
@RequestMapping("/price/delivery-override")
@RequiredArgsConstructor
public class DeliverySkuOverrideController extends BaseController {

    private final DeliverySkuOverrideService deliverySkuOverrideService;

    /**
     * 查询配送点覆盖列表
     */
    @Operation(summary = "查询配送点覆盖列表")
    @PreAuthorize("@ss.hasPermi('price:delivery-override:list')")
    @GetMapping("/list")
    public AjaxResult list(DeliverySkuOverride deliverySkuOverride) {
        List<DeliverySkuOverride> list = deliverySkuOverrideService.selectDeliverySkuOverrideList(deliverySkuOverride);
        return success(list);
    }

    /**
     * 获取配送点覆盖详细信息
     */
    @Operation(summary = "获取配送点覆盖详细信息")
    @PreAuthorize("@ss.hasPermi('price:delivery-override:list')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(deliverySkuOverrideService.selectDeliverySkuOverrideById(id));
    }

    /**
     * 新增/更新配送点覆盖（同配送点+SKU 存在则更新）
     */
    @Operation(summary = "新增/更新配送点覆盖")
    @PreAuthorize("@ss.hasPermi('price:delivery-override:add')")
    @Log(title = "配送点覆盖", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DeliverySkuOverride deliverySkuOverride) {
        return toAjax(deliverySkuOverrideService.saveDeliverySkuOverride(deliverySkuOverride));
    }

    /**
     * 删除配送点覆盖
     */
    @Operation(summary = "删除配送点覆盖")
    @PreAuthorize("@ss.hasPermi('price:delivery-override:remove')")
    @Log(title = "配送点覆盖", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(deliverySkuOverrideService.deleteDeliverySkuOverrideByIds(ids));
    }
}

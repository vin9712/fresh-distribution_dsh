package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.DeliveryPointPrice;
import com.lin.distribution.service.DeliveryPointPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 配送点报价Controller
 *
 * @author dsh
 */
@Tag(name = "配送点报价管理")
@RestController
@RequestMapping("/price/point")
public class DeliveryPointPriceController extends BaseController {
    @Autowired
    private DeliveryPointPriceService deliveryPointPriceService;

    /**
     * 查询配送点报价列表
     */
    @Operation(summary = "查询配送点报价列表")
    @PreAuthorize("@ss.hasPermi('price:point:list')")
    @GetMapping("/list")
    public AjaxResult list(DeliveryPointPrice deliveryPointPrice) {
        List<DeliveryPointPrice> list = deliveryPointPriceService.selectDeliveryPointPriceList(deliveryPointPrice);
        return success(list);
    }

    /**
     * 获取配送点报价详细信息
     */
    @Operation(summary = "获取配送点报价详细信息")
    @PreAuthorize("@ss.hasPermi('price:point:list')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(deliveryPointPriceService.selectDeliveryPointPriceById(id));
    }

    /**
     * 新增配送点报价
     */
    @Operation(summary = "新增配送点报价")
    @PreAuthorize("@ss.hasPermi('price:point:add')")
    @Log(title = "配送点报价", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DeliveryPointPrice deliveryPointPrice) {
        return toAjax(deliveryPointPriceService.insertDeliveryPointPrice(deliveryPointPrice));
    }

    /**
     * 修改配送点报价
     */
    @Operation(summary = "修改配送点报价")
    @PreAuthorize("@ss.hasPermi('price:point:edit')")
    @Log(title = "配送点报价", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DeliveryPointPrice deliveryPointPrice) {
        return toAjax(deliveryPointPriceService.updateDeliveryPointPrice(deliveryPointPrice));
    }

    /**
     * 删除配送点报价
     */
    @Operation(summary = "删除配送点报价")
    @PreAuthorize("@ss.hasPermi('price:point:remove')")
    @Log(title = "配送点报价", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(deliveryPointPriceService.deleteDeliveryPointPriceByIds(ids));
    }
}

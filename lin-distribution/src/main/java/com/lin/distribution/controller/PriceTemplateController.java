package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.PriceTemplate;
import com.lin.distribution.domain.PriceTemplateCustomer;
import com.lin.distribution.domain.PriceTemplateSku;
import com.lin.distribution.service.PriceTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报价模板Controller
 *
 * @author dsh
 */
@Tag(name = "报价模板管理")
@RestController
@RequestMapping("/price/template")
public class PriceTemplateController extends BaseController {
    @Autowired
    private PriceTemplateService priceTemplateService;

    /**
     * 查询报价模板列表
     */
    @Operation(summary = "查询报价模板列表")
    @PreAuthorize("@ss.hasPermi('price:template:list')")
    @GetMapping("/list")
    public AjaxResult list(PriceTemplate priceTemplate) {
        List<PriceTemplate> list = priceTemplateService.selectPriceTemplateList(priceTemplate);
        return success(list);
    }

    /**
     * 获取报价模板详细信息
     */
    @Operation(summary = "获取报价模板详细信息")
    @PreAuthorize("@ss.hasPermi('price:template:list')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(priceTemplateService.selectPriceTemplateById(id));
    }

    /**
     * 新增报价模板
     */
    @Operation(summary = "新增报价模板")
    @PreAuthorize("@ss.hasPermi('price:template:add')")
    @Log(title = "报价模板", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody PriceTemplate priceTemplate) {
        return toAjax(priceTemplateService.insertPriceTemplate(priceTemplate));
    }

    /**
     * 修改报价模板
     */
    @Operation(summary = "修改报价模板")
    @PreAuthorize("@ss.hasPermi('price:template:edit')")
    @Log(title = "报价模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PriceTemplate priceTemplate) {
        return toAjax(priceTemplateService.updatePriceTemplate(priceTemplate));
    }

    /**
     * 删除报价模板
     */
    @Operation(summary = "删除报价模板")
    @PreAuthorize("@ss.hasPermi('price:template:remove')")
    @Log(title = "报价模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(priceTemplateService.deletePriceTemplateByIds(ids));
    }

    /**
     * 查询模板 SKU 价格明细列表
     */
    @Operation(summary = "查询模板SKU价格明细列表")
    @PreAuthorize("@ss.hasPermi('price:template:list')")
    @GetMapping("/{id}/skus")
    public AjaxResult listSkus(@PathVariable("id") Long id) {
        return success(priceTemplateService.selectPriceTemplateSkuList(id));
    }

    /**
     * 新增模板 SKU 价格明细
     */
    @Operation(summary = "新增模板SKU价格明细")
    @PreAuthorize("@ss.hasPermi('price:template:add')")
    @Log(title = "报价模板SKU价格", businessType = BusinessType.INSERT)
    @PostMapping("/{id}/skus")
    public AjaxResult addSku(@PathVariable("id") Long id, @RequestBody PriceTemplateSku priceTemplateSku) {
        priceTemplateSku.setTemplateId(id);
        return toAjax(priceTemplateService.insertPriceTemplateSku(priceTemplateSku));
    }

    /**
     * 修改模板 SKU 价格明细
     */
    @Operation(summary = "修改模板SKU价格明细")
    @PreAuthorize("@ss.hasPermi('price:template:edit')")
    @Log(title = "报价模板SKU价格", businessType = BusinessType.UPDATE)
    @PutMapping("/skus/{skuPriceId}")
    public AjaxResult editSku(@PathVariable("skuPriceId") Long skuPriceId, @RequestBody PriceTemplateSku priceTemplateSku) {
        priceTemplateSku.setId(skuPriceId);
        return toAjax(priceTemplateService.updatePriceTemplateSku(priceTemplateSku));
    }

    /**
     * 删除模板 SKU 价格明细
     */
    @Operation(summary = "删除模板SKU价格明细")
    @PreAuthorize("@ss.hasPermi('price:template:remove')")
    @Log(title = "报价模板SKU价格", businessType = BusinessType.DELETE)
    @DeleteMapping("/skus/{ids}")
    public AjaxResult removeSkus(@PathVariable Long[] ids) {
        return toAjax(priceTemplateService.deletePriceTemplateSkuByIds(ids));
    }

    /**
     * 查询模板绑定客户列表
     */
    @Operation(summary = "查询模板绑定客户列表")
    @PreAuthorize("@ss.hasPermi('price:template:list')")
    @GetMapping("/{id}/customers")
    public AjaxResult listCustomers(@PathVariable("id") Long id) {
        List<PriceTemplateCustomer> list = priceTemplateService.selectPriceTemplateCustomerList(id);
        return success(list);
    }

    /**
     * 绑定客户
     */
    @Operation(summary = "绑定客户")
    @PreAuthorize("@ss.hasPermi('price:template:add')")
    @Log(title = "报价模板", businessType = BusinessType.INSERT)
    @PostMapping("/{id}/bind")
    public AjaxResult bind(@PathVariable("id") Long id, @RequestParam("customerId") Long customerId) {
        return toAjax(priceTemplateService.bindCustomer(id, customerId));
    }

    /**
     * 解绑客户
     */
    @Operation(summary = "解绑客户")
    @PreAuthorize("@ss.hasPermi('price:template:remove')")
    @Log(title = "报价模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}/unbind")
    public AjaxResult unbind(@PathVariable("id") Long id, @RequestParam("customerId") Long customerId) {
        return toAjax(priceTemplateService.unbindCustomer(id, customerId));
    }

    /**
     * 设为默认模板
     */
    @Operation(summary = "设为默认模板")
    @PreAuthorize("@ss.hasPermi('price:template:edit')")
    @Log(title = "报价模板", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/default")
    public AjaxResult setDefault(@PathVariable("id") Long id) {
        return toAjax(priceTemplateService.setDefault(id));
    }
}

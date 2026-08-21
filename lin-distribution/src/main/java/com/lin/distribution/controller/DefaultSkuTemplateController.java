package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.DefaultSkuTemplate;
import com.lin.distribution.service.DefaultSkuTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 默认SKU模板Controller（批量赋值用）
 *
 * @author dsh
 */
@Tag(name = "默认SKU模板管理")
@RestController
@RequestMapping("/product/default-sku-template")
@RequiredArgsConstructor
public class DefaultSkuTemplateController extends BaseController {

    private final DefaultSkuTemplateService defaultSkuTemplateService;

    /** 将 JSON 数值列表安全转为 List<Long> */
    private static List<Long> toLongList(Object raw) {
        if (!(raw instanceof List)) {
            return java.util.Collections.emptyList();
        }
        return ((List<?>) raw).stream()
                .map(o -> o instanceof Number ? ((Number) o).longValue() : Long.valueOf(o.toString()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 查询模板列表
     */
    @Operation(summary = "查询模板列表")
    @PreAuthorize("@ss.hasPermi('product:default-sku-template:list')")
    @GetMapping("/list")
    public AjaxResult list(DefaultSkuTemplate defaultSkuTemplate) {
        return success(defaultSkuTemplateService.selectDefaultSkuTemplateList(defaultSkuTemplate));
    }

    /**
     * 获取模板详细信息
     */
    @Operation(summary = "获取模板详细信息")
    @PreAuthorize("@ss.hasPermi('product:default-sku-template:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(defaultSkuTemplateService.selectDefaultSkuTemplateById(id));
    }

    /**
     * 查询模板明细（SKU集合）
     */
    @Operation(summary = "查询模板明细SKU集合")
    @PreAuthorize("@ss.hasPermi('product:default-sku-template:query')")
    @GetMapping(value = "/{id}/items")
    public AjaxResult items(@PathVariable("id") Long id) {
        return success(defaultSkuTemplateService.selectTemplateItems(id));
    }

    /**
     * 新增模板（body: { "template": {...}, "skuIds": [1,2] }）
     */
    @Operation(summary = "新增模板")
    @PreAuthorize("@ss.hasPermi('product:default-sku-template:add')")
    @Log(title = "默认SKU模板", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Map<String, Object> request) {
        DefaultSkuTemplate template = new DefaultSkuTemplate();
        template.setName((String) request.get("name"));
        template.setCustomerGroupId(request.get("customerGroupId") == null ? null : ((Number) request.get("customerGroupId")).longValue());
        template.setStatus(request.get("status") == null ? 1 : ((Number) request.get("status")).intValue());
        List<Long> skuIds = toLongList(request.get("skuIds"));
        return toAjax(defaultSkuTemplateService.insertDefaultSkuTemplate(template, skuIds));
    }

    /**
     * 修改模板（body: { "template": {...}, "skuIds": [1,2] }）
     */
    @Operation(summary = "修改模板")
    @PreAuthorize("@ss.hasPermi('product:default-sku-template:edit')")
    @Log(title = "默认SKU模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Map<String, Object> request) {
        DefaultSkuTemplate template = new DefaultSkuTemplate();
        template.setId(((Number) request.get("id")).longValue());
        template.setName((String) request.get("name"));
        template.setCustomerGroupId(request.get("customerGroupId") == null ? null : ((Number) request.get("customerGroupId")).longValue());
        template.setStatus(request.get("status") == null ? 1 : ((Number) request.get("status")).intValue());
        List<Long> skuIds = toLongList(request.get("skuIds"));
        return toAjax(defaultSkuTemplateService.updateDefaultSkuTemplate(template, skuIds));
    }

    /**
     * 删除模板
     */
    @Operation(summary = "删除模板")
    @PreAuthorize("@ss.hasPermi('product:default-sku-template:remove')")
    @Log(title = "默认SKU模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(defaultSkuTemplateService.deleteDefaultSkuTemplateByIds(ids));
    }
}

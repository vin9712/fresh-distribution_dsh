package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.core.page.TableDataInfo;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.CustomerSku;
import com.lin.distribution.service.CustomerSkuService;
import com.lin.distribution.service.DefaultSkuTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 客户商品Controller（customers_sku：客户商品池与个性化）
 *
 * @author dsh
 */
@Tag(name = "客户商品管理")
@RestController
@RequestMapping("/product/customer-sku")
@RequiredArgsConstructor
public class CustomerSkuController extends BaseController {

    private final CustomerSkuService customerSkuService;
    private final DefaultSkuTemplateService defaultSkuTemplateService;

    /** 将 JSON 数值列表安全转为 List<Long>（Jackson 默认 Integer） */
    private static List<Long> toLongList(Object raw) {
        if (!(raw instanceof List)) {
            return java.util.Collections.emptyList();
        }
        return ((List<?>) raw).stream()
                .map(o -> o instanceof Number ? ((Number) o).longValue() : Long.valueOf(o.toString()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 分页查询客户商品
     */
    @Operation(summary = "分页查询客户商品")
    @PreAuthorize("@ss.hasPermi('product:customer-sku:list')")
    @GetMapping("/page")
    public TableDataInfo page(CustomerSku customerSku) {
        startPage();
        List<CustomerSku> list = customerSkuService.selectCustomerSkuList(customerSku);
        return getDataTable(list);
    }

    /**
     * 客户商品列表（合并配送点覆盖，供录单选择商品）
     */
    @Operation(summary = "客户商品列表（含配送点覆盖）")
    @PreAuthorize("@ss.hasPermi('product:customer-sku:list')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam Long customerId,
                           @RequestParam(required = false) Long deliveryPointId,
                           @RequestParam(required = false) String keyword) {
        return success(customerSkuService.listCustomerProducts(customerId, deliveryPointId, keyword));
    }

    /**
     * 客户商品池（不合并配送点覆盖，管理页用）
     */
    @Operation(summary = "客户商品池列表")
    @PreAuthorize("@ss.hasPermi('product:customer-sku:list')")
    @GetMapping("/pool")
    public AjaxResult pool(CustomerSku customerSku) {
        return success(customerSkuService.selectCustomerSkuList(customerSku));
    }

    /**
     * 获取客户商品详细信息
     */
    @Operation(summary = "获取客户商品详细信息")
    @PreAuthorize("@ss.hasPermi('product:customer-sku:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(customerSkuService.selectCustomerSkuById(id));
    }

    /**
     * 新增客户商品（单个指派）
     */
    @Operation(summary = "新增客户商品")
    @PreAuthorize("@ss.hasPermi('product:customer-sku:add')")
    @Log(title = "客户商品", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CustomerSku customerSku) {
        return toAjax(customerSkuService.insertCustomerSku(customerSku));
    }

    /**
     * 个性化修改（置 is_follow_default=0，不再跟随默认模板）
     */
    @Operation(summary = "修改客户商品（个性化）")
    @PreAuthorize("@ss.hasPermi('product:customer-sku:edit')")
    @Log(title = "客户商品", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CustomerSku customerSku) {
        customerSku.setIsFollowDefault(0);
        customerSku.setSourceTemplateId(null);
        return toAjax(customerSkuService.updateCustomerSku(customerSku));
    }

    /**
     * 批量赋值默认SKU（deepseek_redesign.md §5.1）
     * body: { "skuIds": [1,2], "customerIds": [1,2,3], "strategy": 1, "templateId": null }
     */
    @Operation(summary = "批量赋值默认SKU")
    @PreAuthorize("@ss.hasPermi('product:customer-sku:assign')")
    @Log(title = "客户商品批量赋值", businessType = BusinessType.INSERT)
    @PostMapping("/assign")
    public AjaxResult assign(@RequestBody Map<String, Object> request) {
        List<Long> skuIds = toLongList(request.get("skuIds"));
        List<Long> customerIds = toLongList(request.get("customerIds"));
        int strategy = request.get("strategy") == null ? 1 : ((Number) request.get("strategy")).intValue();
        Long templateId = request.get("templateId") == null ? null : ((Number) request.get("templateId")).longValue();
        return success(defaultSkuTemplateService.batchAssign(skuIds, customerIds, strategy, templateId));
    }

    /**
     * 删除客户商品
     */
    @Operation(summary = "删除客户商品")
    @PreAuthorize("@ss.hasPermi('product:customer-sku:remove')")
    @Log(title = "客户商品", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(customerSkuService.deleteCustomerSkuByIds(ids));
    }
}

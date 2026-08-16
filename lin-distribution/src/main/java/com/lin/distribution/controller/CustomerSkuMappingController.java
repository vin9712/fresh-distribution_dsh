package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.CustomerSkuMapping;
import com.lin.distribution.service.CustomerSkuMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户SKU映射Controller
 *
 * @author lin
 * @date 2024-11-20
 */
@Tag(name = "客户SKU映射管理")
@RestController
@RequestMapping("/product/mapping")
public class CustomerSkuMappingController extends BaseController {
    @Autowired
    private CustomerSkuMappingService customerSkuMappingService;

    /**
     * 查询客户SKU映射列表
     */
    @Operation(summary = "查询客户SKU映射列表")
    @PreAuthorize("@ss.hasPermi('product:aliasMapping:list')")
    @GetMapping("/list")
    public AjaxResult list(CustomerSkuMapping customerSkuMapping) {
        List<CustomerSkuMapping> list = customerSkuMappingService.selectCustomerSkuMappingList(customerSkuMapping);
        return success(list);
    }

    /**
     * 获取客户SKU映射详细信息
     */
    @Operation(summary = "获取客户SKU映射详细信息")
    @PreAuthorize("@ss.hasPermi('product:aliasMapping:list')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(customerSkuMappingService.selectCustomerSkuMappingById(id));
    }

    /**
     * 新增客户SKU映射
     */
    @Operation(summary = "新增客户SKU映射")
    @PreAuthorize("@ss.hasPermi('product:mapping:add')")
    @Log(title = "客户SKU映射", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CustomerSkuMapping customerSkuMapping) {
        return toAjax(customerSkuMappingService.insertCustomerSkuMapping(customerSkuMapping));
    }

    /**
     * 修改客户SKU映射
     */
    @Operation(summary = "修改客户SKU映射")
    @PreAuthorize("@ss.hasPermi('product:mapping:edit')")
    @Log(title = "客户SKU映射", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CustomerSkuMapping customerSkuMapping) {
        return toAjax(customerSkuMappingService.updateCustomerSkuMapping(customerSkuMapping));
    }

    /**
     * 删除客户SKU映射
     */
    @Operation(summary = "删除客户SKU映射")
    @PreAuthorize("@ss.hasPermi('product:mapping:remove')")
    @Log(title = "客户SKU映射", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(customerSkuMappingService.deleteCustomerSkuMappingByIds(ids));
    }
}

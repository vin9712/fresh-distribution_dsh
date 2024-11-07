package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.distribution.domain.ProductCategory;
import com.lin.distribution.service.ProductCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品分类Controller
 *
 * @author lin
 * @date 2024-11-02
 */
@Tag(name = "商品分类管理")
@RestController
@RequestMapping("/product/category")
public class ProductCategoryController extends BaseController {
    @Autowired
    private ProductCategoryService productCategoryService;

    /**
     * 查询商品分类列表
     */
    @Operation(summary = "查询商品分类列表")
    @PreAuthorize("@ss.hasPermi('product:category:list')")
    @GetMapping("/list")
    public AjaxResult list(ProductCategory productCategory) {
        List<ProductCategory> list = productCategoryService.selectProductCategoryList(productCategory);
        return success(list);
    }

    /**
     * 导出商品分类列表
     */
    @Operation(summary = "导出商品分类列表")
    @PreAuthorize("@ss.hasPermi('product:category:export')")
    @Log(title = "商品分类", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProductCategory productCategory) {
        List<ProductCategory> list = productCategoryService.selectProductCategoryList(productCategory);
        ExcelUtil<ProductCategory> util = new ExcelUtil<ProductCategory>(ProductCategory.class);
        util.exportExcel(response, list, "商品分类数据");
    }

    /**
     * 获取商品分类详细信息
     */
    @Operation(summary = "获取商品分类详细信息")
    @PreAuthorize("@ss.hasPermi('product:category:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(productCategoryService.selectProductCategoryById(id));
    }

    /**
     * 获取新增商品分类排序
     */
    @Operation(summary = "获取新增商品分类排序")
    @GetMapping(value = "/nextSort/{id}")
    public AjaxResult getNextProductCategorySort(@PathVariable("id") Long id) {
        return success(productCategoryService.getNextProductCategorySort(id));
    }

    /**
     * 新增商品分类
     */
    @Operation(summary = "新增商品分类")
    @PreAuthorize("@ss.hasPermi('product:category:add')")
    @Log(title = "商品分类", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ProductCategory productCategory) {
        return toAjax(productCategoryService.insertProductCategory(productCategory));
    }

    /**
     * 修改商品分类
     */
    @Operation(summary = "修改商品分类")
    @PreAuthorize("@ss.hasPermi('product:category:edit')")
    @Log(title = "商品分类", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ProductCategory productCategory) {
        return toAjax(productCategoryService.updateProductCategory(productCategory));
    }

    /**
     * 删除商品分类
     */
    @Operation(summary = "删除商品分类")
    @PreAuthorize("@ss.hasPermi('product:category:remove')")
    @Log(title = "商品分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(productCategoryService.deleteProductCategoryByIds(ids));
    }
}

package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.ProductAlias;
import com.lin.distribution.service.ProductAliasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品全局别名Controller
 *
 * @author lin
 * @date 2024-11-20
 */
@Tag(name = "商品全局别名管理")
@RestController
@RequestMapping("/product/alias")
public class ProductAliasController extends BaseController {
    @Autowired
    private ProductAliasService productAliasService;

    /**
     * 查询商品全局别名列表
     */
    @Operation(summary = "查询商品全局别名列表")
    @PreAuthorize("@ss.hasPermi('product:aliasMapping:list')")
    @GetMapping("/list")
    public AjaxResult list(ProductAlias productAlias) {
        List<ProductAlias> list = productAliasService.selectProductAliasList(productAlias);
        return success(list);
    }

    /**
     * 按关键词检索别名（供录单检索）
     */
    @Operation(summary = "按关键词检索别名")
    @GetMapping("/keyword/{keyword}")
    public AjaxResult listByKeyword(@PathVariable("keyword") String keyword) {
        return success(productAliasService.listByKeyword(keyword));
    }

    /**
     * 获取商品全局别名详细信息
     */
    @Operation(summary = "获取商品全局别名详细信息")
    @PreAuthorize("@ss.hasPermi('product:aliasMapping:list')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(productAliasService.selectProductAliasById(id));
    }

    /**
     * 新增商品全局别名
     */
    @Operation(summary = "新增商品全局别名")
    @PreAuthorize("@ss.hasPermi('product:alias:add')")
    @Log(title = "商品全局别名", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ProductAlias productAlias) {
        return toAjax(productAliasService.insertProductAlias(productAlias));
    }

    /**
     * 修改商品全局别名
     */
    @Operation(summary = "修改商品全局别名")
    @PreAuthorize("@ss.hasPermi('product:alias:edit')")
    @Log(title = "商品全局别名", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ProductAlias productAlias) {
        return toAjax(productAliasService.updateProductAlias(productAlias));
    }

    /**
     * 删除商品全局别名
     */
    @Operation(summary = "删除商品全局别名")
    @PreAuthorize("@ss.hasPermi('product:alias:remove')")
    @Log(title = "商品全局别名", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(productAliasService.deleteProductAliasByIds(ids));
    }
}

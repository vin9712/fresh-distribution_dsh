package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.TempProduct;
import com.lin.distribution.dto.TempProductConvertDTO;
import com.lin.distribution.service.TempProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 临时商品Controller
 *
 * @author lin
 * @date 2024-11-20
 */
@Tag(name = "临时商品管理")
@RestController
@RequestMapping("/product/temp")
public class TempProductController extends BaseController {
    @Autowired
    private TempProductService tempProductService;

    /**
     * 查询临时商品列表
     */
    @Operation(summary = "查询临时商品列表")
    @PreAuthorize("@ss.hasPermi('product:aliasMapping:list')")
    @GetMapping("/list")
    public AjaxResult list(TempProduct tempProduct) {
        List<TempProduct> list = tempProductService.selectTempProductList(tempProduct);
        return success(list);
    }

    /**
     * 获取临时商品详细信息
     */
    @Operation(summary = "获取临时商品详细信息")
    @PreAuthorize("@ss.hasPermi('product:aliasMapping:list')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(tempProductService.selectTempProductById(id));
    }

    /**
     * 新增临时商品
     */
    @Operation(summary = "新增临时商品")
    @PreAuthorize("@ss.hasPermi('product:temp:add')")
    @Log(title = "临时商品", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TempProduct tempProduct) {
        return toAjax(tempProductService.insertTempProduct(tempProduct));
    }

    /**
     * 修改临时商品
     */
    @Operation(summary = "修改临时商品")
    @PreAuthorize("@ss.hasPermi('product:temp:edit')")
    @Log(title = "临时商品", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TempProduct tempProduct) {
        return toAjax(tempProductService.updateTempProduct(tempProduct));
    }

    /**
     * 删除临时商品
     */
    @Operation(summary = "删除临时商品")
    @PreAuthorize("@ss.hasPermi('product:temp:remove')")
    @Log(title = "临时商品", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(tempProductService.deleteTempProductByIds(ids));
    }

    /**
     * 临时商品转正为正式SKU
     */
    @Operation(summary = "临时商品转正为正式SKU")
    @PreAuthorize("@ss.hasPermi('product:temp:convert')")
    @Log(title = "临时商品", businessType = BusinessType.INSERT)
    @PostMapping("/{id}/convert")
    public AjaxResult convert(@PathVariable("id") Long id, @RequestBody(required = false) TempProductConvertDTO dto) {
        if (dto == null) {
            dto = new TempProductConvertDTO();
        }
        dto.setId(id);
        return success(tempProductService.convertToSku(dto));
    }
}

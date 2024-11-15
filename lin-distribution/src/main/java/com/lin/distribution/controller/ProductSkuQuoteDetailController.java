package com.lin.distribution.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.ProductSkuQuoteDetail;
import com.lin.distribution.service.ProductSkuQuoteDetailService;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.common.core.page.TableDataInfo;

/**
 * 商品报价明细Controller
 *
 * @author lin
 * @date 2024-11-15
 */
@Tag(name = "商品报价明细接口")
@RestController
@RequestMapping("/quote/quoteDetail")
public class ProductSkuQuoteDetailController extends BaseController {
    @Autowired
    private ProductSkuQuoteDetailService productSkuQuoteDetailService;

    /**
     * 分页查询商品报价明细列表
     */
    @PreAuthorize("@ss.hasPermi('quote:quoteDetail:list')")
    @GetMapping("/page")
    public TableDataInfo page(ProductSkuQuoteDetail productSkuQuoteDetail) {
        startPage();
        List<ProductSkuQuoteDetail> list = productSkuQuoteDetailService.selectProductSkuQuoteDetailList(productSkuQuoteDetail);
        return getDataTable(list);
    }

    /**
     * 查询商品报价明细列表
     */
    @PreAuthorize("@ss.hasPermi('quote:quoteDetail:list')")
    @GetMapping("/list")
    public AjaxResult list(ProductSkuQuoteDetail productSkuQuoteDetail) {
        List<ProductSkuQuoteDetail> list = productSkuQuoteDetailService.selectProductSkuQuoteDetailList(productSkuQuoteDetail);
        return success(list);
    }

    /**
     * 导出商品报价明细列表
     */
    @PreAuthorize("@ss.hasPermi('quote:quoteDetail:export')")
    @Log(title = "商品报价明细", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProductSkuQuoteDetail productSkuQuoteDetail) {
        List<ProductSkuQuoteDetail> list = productSkuQuoteDetailService.selectProductSkuQuoteDetailList(productSkuQuoteDetail);
        ExcelUtil<ProductSkuQuoteDetail> util = new ExcelUtil<ProductSkuQuoteDetail>(ProductSkuQuoteDetail.class);
        util.exportExcel(response, list, "商品报价明细数据");
    }

    /**
     * 获取商品报价明细详细信息
     */
    @PreAuthorize("@ss.hasPermi('quote:quoteDetail:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(productSkuQuoteDetailService.selectProductSkuQuoteDetailById(id));
    }

    /**
     * 新增商品报价明细
     */
    @PreAuthorize("@ss.hasPermi('quote:quoteDetail:add')")
    @Log(title = "商品报价明细", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ProductSkuQuoteDetail productSkuQuoteDetail) {
        return toAjax(productSkuQuoteDetailService.insertProductSkuQuoteDetail(productSkuQuoteDetail));
    }

    /**
     * 修改商品报价明细
     */
    @PreAuthorize("@ss.hasPermi('quote:quoteDetail:edit')")
    @Log(title = "商品报价明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ProductSkuQuoteDetail productSkuQuoteDetail) {
        return toAjax(productSkuQuoteDetailService.updateProductSkuQuoteDetail(productSkuQuoteDetail));
    }

    /**
     * 删除商品报价明细
     */
    @PreAuthorize("@ss.hasPermi('quote:quoteDetail:remove')")
    @Log(title = "商品报价明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(productSkuQuoteDetailService.deleteProductSkuQuoteDetailByIds(ids));
    }
}

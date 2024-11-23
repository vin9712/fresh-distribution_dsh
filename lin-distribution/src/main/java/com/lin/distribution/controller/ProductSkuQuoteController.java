package com.lin.distribution.controller;

import java.util.List;

import com.lin.distribution.constant.ProductSkuQuoteStatus;
import com.lin.distribution.dto.ProductSkuQuoteCreateDTO;
import com.lin.distribution.dto.ProductSkuQuoteUpdateStatusDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.ProductSkuQuote;
import com.lin.distribution.service.ProductSkuQuoteService;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.common.core.page.TableDataInfo;

/**
 * 商品报价Controller
 *
 * @author lin
 * @date 2024-11-14
 */
@Tag(name = "商品报价接口")
@RestController
@RequestMapping("/product/quote")
public class ProductSkuQuoteController extends BaseController {
    @Autowired
    private ProductSkuQuoteService productSkuQuoteService;

    /**
     * 分页查询商品报价列表
     */
    @PreAuthorize("@ss.hasPermi('product:quote:list')")
    @GetMapping("/page")
    public TableDataInfo page(ProductSkuQuote productSkuQuote) {
        startPage();
        List<ProductSkuQuote> list = productSkuQuoteService.selectProductSkuQuoteList(productSkuQuote);
        return getDataTable(list);
    }

    /**
     * 查询商品报价列表
     */
    @PreAuthorize("@ss.hasPermi('product:quote:list')")
    @GetMapping("/list")
    public AjaxResult list(ProductSkuQuote productSkuQuote) {
        List<ProductSkuQuote> list = productSkuQuoteService.selectProductSkuQuoteList(productSkuQuote);
        return success(list);
    }

    /**
     * 导出商品报价列表
     */
    @PreAuthorize("@ss.hasPermi('product:quote:export')")
    @Log(title = "商品报价", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProductSkuQuote productSkuQuote) {
        List<ProductSkuQuote> list = productSkuQuoteService.selectProductSkuQuoteList(productSkuQuote);
        ExcelUtil<ProductSkuQuote> util = new ExcelUtil<ProductSkuQuote>(ProductSkuQuote.class);
        util.exportExcel(response, list, "商品报价数据");
    }

    /**
     * 获取商品报价详细信息
     */
    @PreAuthorize("@ss.hasPermi('product:quote:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(productSkuQuoteService.selectProductSkuQuoteById(id));
    }

    /**
     * 新增商品报价
     */
    @PreAuthorize("@ss.hasPermi('product:quote:add')")
    @Log(title = "商品报价", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ProductSkuQuote productSkuQuote) {
        return toAjax(productSkuQuoteService.insertProductSkuQuote(productSkuQuote));
    }

    /**
     * 修改商品报价
     */
    @PreAuthorize("@ss.hasPermi('product:quote:edit')")
    @Log(title = "商品报价", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ProductSkuQuote productSkuQuote) {
        return toAjax(productSkuQuoteService.updateProductSkuQuote(productSkuQuote));
    }

    /**
     * 删除商品报价
     */
    @PreAuthorize("@ss.hasPermi('product:quote:remove')")
    @Log(title = "商品报价", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(productSkuQuoteService.deleteProductSkuQuoteByIds(ids));
    }

    /**
     * 获取或生成商品报价单号
     */
    @GetMapping("/code")
    public AjaxResult generateSkuQuoteNo(@RequestParam(name = "refresh", required = false, defaultValue = "false") Boolean refresh,
                                         @RequestParam(name = "currentCode", required = false) String currentCode) {
        return success(productSkuQuoteService.generateSkuQuoteNo(refresh, currentCode));
    }

    /**
     * 创建商品报价+详情
     *
     * @param request
     * @return
     */
    @PostMapping("/create")
    public AjaxResult createSkuQuote(@RequestBody @Validated ProductSkuQuoteCreateDTO request) {
        return success(productSkuQuoteService.createSkuQuote(request));
    }

    /**
     * @param request
     * @return
     */
    @PutMapping("/update")
    public AjaxResult updateSkuQuote(@RequestBody @Validated ProductSkuQuoteCreateDTO request) {
        return success(productSkuQuoteService.updateSkuQuote(request));
    }

    /**
     * 更新报价单状态
     *
     * @return
     */
    @PutMapping("/status")
    public AjaxResult updateQuoteStatus(@RequestBody @Validated ProductSkuQuoteUpdateStatusDTO requset) {
        productSkuQuoteService.updateQuoteStatus(requset);
        return success();
    }

    @GetMapping("/active/{customerId}")
    public AjaxResult getCustomerActiveQuote(@PathVariable("customerId") Long customerId) {
        return success(productSkuQuoteService.getCustomerActiveQuote(customerId));
    }
}

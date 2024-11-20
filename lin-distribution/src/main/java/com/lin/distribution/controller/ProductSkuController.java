package com.lin.distribution.controller;

import java.util.List;

import com.lin.distribution.service.ProductService;
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
import com.lin.distribution.domain.ProductSku;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.common.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 商品信息Controller
 *
 * @author lin
 * @date 2024-11-11
 */
@RestController
@RequestMapping("/product/sku")
public class ProductSkuController extends BaseController {
    @Autowired
    private ProductService productSkuService;

    /**
     * 分页查询商品信息列表
     */
    @PreAuthorize("@ss.hasPermi('product:sku:list')")
    @GetMapping("/page")
    public TableDataInfo page(ProductSku productSku) {
        startPage();
        List<ProductSku> list = productSkuService.selectProductSkuList(productSku);
        return getDataTable(list);
    }

    /**
     * 查询商品信息列表
     */
    @PreAuthorize("@ss.hasPermi('product:sku:list')")
    @GetMapping("/list")
    public AjaxResult list(ProductSku productSku) {
        List<ProductSku> list = productSkuService.selectProductSkuList(productSku);
        return success(list);
    }

    /**
     * 导出商品信息列表
     */
    @PreAuthorize("@ss.hasPermi('product:sku:export')")
    @Log(title = "商品信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProductSku productSku) {
        List<ProductSku> list = productSkuService.selectProductSkuList(productSku);
        ExcelUtil<ProductSku> util = new ExcelUtil<ProductSku>(ProductSku.class);
        util.exportExcel(response, list, "商品信息数据");
    }

    /**
     * 获取商品信息详细信息
     */
    @PreAuthorize("@ss.hasPermi('product:sku:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(productSkuService.selectProductSkuById(id));
    }

    /**
     * 新增商品信息
     */
    @PreAuthorize("@ss.hasPermi('product:sku:add')")
    @Log(title = "商品信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ProductSku productSku) {
        return toAjax(productSkuService.insertProductSku(productSku));
    }

    /**
     * 修改商品信息
     */
    @PreAuthorize("@ss.hasPermi('product:sku:edit')")
    @Log(title = "商品信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ProductSku productSku) {
        return toAjax(productSkuService.updateProductSku(productSku));
    }

    /**
     * 删除商品信息
     */
    @PreAuthorize("@ss.hasPermi('product:sku:remove')")
    @Log(title = "商品信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable String[] ids) {
        return toAjax(productSkuService.deleteProductSkuByIds(ids));
    }

    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file) throws Exception {
        ExcelUtil<ProductSku> util = new ExcelUtil<>(ProductSku.class);
        List<ProductSku> skuList = util.importExcel(file.getInputStream());
        String message = productSkuService.importProductSku(skuList);
        return AjaxResult.success(message);
    }

    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil<ProductSku> util = new ExcelUtil<>(ProductSku.class);
        util.importTemplateExcel(response, "商品信息");
    }
}

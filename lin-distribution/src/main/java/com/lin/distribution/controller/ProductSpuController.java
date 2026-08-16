package com.lin.distribution.controller;

import java.util.List;

import com.lin.distribution.service.ProductService;
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
import com.lin.distribution.domain.ProductSpu;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.common.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 商品spuController
 *
 * @author lin
 * @date 2024-11-07
 */
@Tag(name = "商品spu管理")
@RestController
@RequestMapping("/product/spu")
public class ProductSpuController extends BaseController {
    @Autowired
    private ProductService productSpuService;

    /**
     * 分页查询商品spu列表
     */
    @PreAuthorize("@ss.hasPermi('product:spu:list')")
    @GetMapping("/page")
    public TableDataInfo page(ProductSpu productSpu) {
        startPage();
        List<ProductSpu> list = productSpuService.selectProductSpuList(productSpu);
        return getDataTable(list);
    }

    /**
     * 查询商品spu列表
     */
    @PreAuthorize("@ss.hasPermi('product:spu:list')")
    @GetMapping("/list")
    public AjaxResult list(ProductSpu productSpu) {
        List<ProductSpu> list = productSpuService.selectProductSpuList(productSpu);
        return success(list);
    }

    /**
     * 导出商品spu列表
     */
    @PreAuthorize("@ss.hasPermi('product:spu:export')")
    @Log(title = "商品spu", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProductSpu productSpu) {
        List<ProductSpu> list = productSpuService.selectProductSpuList(productSpu);
        ExcelUtil<ProductSpu> util = new ExcelUtil<ProductSpu>(ProductSpu.class);
        util.exportExcel(response, list, "商品spu数据");
    }

    /**
     * 导入商品库（模板=导出模板；同分类+名称重复跳过）
     */
    @PreAuthorize("@ss.hasPermi('product:spu:import')")
    @Log(title = "商品spu", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file) throws Exception {
        ExcelUtil<ProductSpu> util = new ExcelUtil<ProductSpu>(ProductSpu.class);
        List<ProductSpu> spuList = util.importExcel(file.getInputStream());
        String message = productSpuService.importProductSpu(spuList);
        return AjaxResult.success(message);
    }

    /**
     * 下载商品库导入模板
     */
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil<ProductSpu> util = new ExcelUtil<ProductSpu>(ProductSpu.class);
        util.importTemplateExcel(response, "商品库");
    }

    /**
     * 获取商品spu详细信息
     */
    @PreAuthorize("@ss.hasPermi('product:spu:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(productSpuService.selectProductSpuById(id));
    }

    /**
     * 新增商品spu
     */
    @PreAuthorize("@ss.hasPermi('product:spu:add')")
    @Log(title = "商品spu", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ProductSpu productSpu) {
        return success(productSpuService.insertProductSpu(productSpu));
    }

    /**
     * 修改商品spu
     */
    @PreAuthorize("@ss.hasPermi('product:spu:edit')")
    @Log(title = "商品spu", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ProductSpu productSpu) {
        return toAjax(productSpuService.updateProductSpu(productSpu));
    }

    /**
     * 删除商品spu
     */
    @PreAuthorize("@ss.hasPermi('product:spu:remove')")
    @Log(title = "商品spu", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(productSpuService.deleteProductSpuByIds(ids));
    }
}
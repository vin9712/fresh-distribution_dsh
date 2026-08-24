package com.lin.distribution.controller;

import java.util.List;

import com.lin.distribution.constant.ProductSkuQuoteStatus;
import com.lin.distribution.dto.ProductSkuQuoteCreateDTO;
import com.lin.distribution.dto.ProductSkuQuoteImportDTO;
import com.lin.distribution.dto.ProductSkuQuoteUpdateStatusDTO;
import com.lin.distribution.dto.QuotePriceImportConfirmDTO;
import com.lin.distribution.dto.QuotePriceImportDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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

    /**
     * 导入客户报价：多客户多行，按客户聚合生成报价单
     */
    @PreAuthorize("@ss.hasPermi('product:quote:import')")
    @Log(title = "商品报价", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file) throws Exception {
        ExcelUtil<ProductSkuQuoteImportDTO> util = new ExcelUtil<>(ProductSkuQuoteImportDTO.class);
        List<ProductSkuQuoteImportDTO> rows = util.importExcel(file.getInputStream());
        String message = productSkuQuoteService.importQuoteData(rows);
        return AjaxResult.success(message);
    }

    /**
     * 下载客户报价导入模板
     */
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil<ProductSkuQuoteImportDTO> util = new ExcelUtil<>(ProductSkuQuoteImportDTO.class);
        util.importTemplateExcel(response, "客户报价");
    }

    /**
     * 粘贴价格表导入预览：解析并自动匹配内部 SKU
     */
    @PreAuthorize("@ss.hasPermi('product:quote:import')")
    @PostMapping("/importPreview")
    public AjaxResult importPreview(@RequestBody QuotePriceImportDTO.PreviewReq req) {
        return AjaxResult.success(productSkuQuoteService.previewQuotePriceImport(req.getCustomerId(), req.getText()));
    }

    /**
     * 价格表Excel导入预览：上传文件解析并自动匹配内部 SKU
     */
    @PreAuthorize("@ss.hasPermi('product:quote:import')")
    @PostMapping("/importPreviewExcel")
    public AjaxResult importPreviewExcel(@RequestParam("file") MultipartFile file,
                                         @RequestParam("customerId") Long customerId) throws Exception {
        return AjaxResult.success(productSkuQuoteService.previewQuotePriceImportExcel(customerId, file.getInputStream()));
    }

    /**
     * 下载价格表导入模板（EasyExcel 生成）
     */
    @PostMapping("/importPriceTemplate")
    public void importPriceTemplate(HttpServletResponse response) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = java.net.URLEncoder.encode("价格表导入模板", java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        List<List<String>> head = List.of(
                List.of("商品名称（或客户叫法）"),
                List.of("单位（选填）"),
                List.of("价格"));
        List<List<Object>> rows = List.of(
                List.of("土豆", "斤", 2.5),
                List.of("黄心土豆/5斤装", "", 45));
        com.alibaba.excel.EasyExcel.write(response.getOutputStream())
                .head(head)
                .registerWriteHandler(new com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy())
                .sheet("价格表")
                .doWrite(rows);
    }

    /**
     * 价格表导入确认：已匹配行生成报价单草稿，未匹配行可转临时商品
     */
    @PreAuthorize("@ss.hasPermi('product:quote:import')")
    @Log(title = "商品报价", businessType = BusinessType.IMPORT)
    @PostMapping("/importConfirm")
    public AjaxResult importConfirm(@RequestBody QuotePriceImportConfirmDTO dto) {
        // 返回报告字符串放在 msg 中，前端弹窗展示
        return AjaxResult.success(productSkuQuoteService.confirmQuotePriceImport(dto));
    }
}

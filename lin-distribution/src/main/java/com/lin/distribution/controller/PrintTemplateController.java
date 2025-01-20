package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.core.page.TableDataInfo;
import com.lin.common.enums.BusinessType;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.dto.print.PrintTemplateExcelRequestDTO;
import com.lin.distribution.service.PrintTemplateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 打印模板Controller
 *
 * @author lin
 * @date 2024-12-18
 */
@Tag(name = "打印模板接口")
@RestController
@RequestMapping("/print/template")
public class PrintTemplateController extends BaseController {
    @Autowired
    private PrintTemplateService printTemplateService;

    /**
     * 分页查询打印模板列表
     */
    @PreAuthorize("@ss.hasPermi('print:template:list')")
    @GetMapping("/page")
    public TableDataInfo page(PrintTemplate printTemplate) {
        startPage();
        List<PrintTemplate> list = printTemplateService.selectPrintTemplateList(printTemplate);
        return getDataTable(list);
    }

    /**
     * 查询打印模板列表
     */
    @PreAuthorize("@ss.hasPermi('print:template:list')")
    @GetMapping("/list")
    public AjaxResult list(PrintTemplate printTemplate) {
        List<PrintTemplate> list = printTemplateService.selectPrintTemplateList(printTemplate);
        return success(list);
    }

    /**
     * 获取或生成打印模板编号
     */
    @GetMapping("/code")
    public AjaxResult generatePrintTemplateNo(@RequestParam(name = "refresh", required = false, defaultValue = "false") Boolean refresh,
                                              @RequestParam(name = "currentCode", required = false) String currentCode) {
        return success(printTemplateService.generatePrintTemplateNo(refresh, currentCode));
    }

    /**
     * 导出打印模板列表
     */
    @PreAuthorize("@ss.hasPermi('print:template:export')")
    @Log(title = "打印模板", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, PrintTemplate printTemplate) {
        List<PrintTemplate> list = printTemplateService.selectPrintTemplateList(printTemplate);
        ExcelUtil<PrintTemplate> util = new ExcelUtil<PrintTemplate>(PrintTemplate.class);
        util.exportExcel(response, list, "打印模板数据");
    }

    /**
     * 导出 Excel
     * @param request
     * @param response
     */
    @PostMapping("/download")
    public void downloadPrintTemplateExcel(PrintTemplateExcelRequestDTO request, HttpServletResponse response) {
        printTemplateService.downloadPrintTemplateExcel(request, response);
    }

    /**
     * 获取打印模板详细信息
     */
    @PreAuthorize("@ss.hasPermi('print:template:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(printTemplateService.selectPrintTemplateById(id));
    }

    /**
     * 新增打印模板
     */
    @PreAuthorize("@ss.hasPermi('print:template:add')")
    @Log(title = "打印模板", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody PrintTemplate printTemplate) {
        return success(printTemplateService.insertPrintTemplate(printTemplate));
    }

    /**
     * 修改打印模板
     */
    @PreAuthorize("@ss.hasPermi('print:template:edit')")
    @Log(title = "打印模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PrintTemplate printTemplate) {
        return toAjax(printTemplateService.updatePrintTemplate(printTemplate));
    }

    /**
     * 删除打印模板
     */
    @PreAuthorize("@ss.hasPermi('print:template:remove')")
    @Log(title = "打印模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(printTemplateService.deletePrintTemplateByIds(ids));
    }
}

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.service.PrintTemplateService;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.common.core.page.TableDataInfo;

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
        return toAjax(printTemplateService.insertPrintTemplate(printTemplate));
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

    /**
     * 测试发布模板（W0-4.4：打测试水印，不计正式次数）
     */
    @PreAuthorize("@ss.hasPermi('print:template:edit')")
    @Log(title = "打印模板", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/test-publish")
    public AjaxResult testPublish(@PathVariable("id") Long id) {
        return toAjax(printTemplateService.testPublish(id));
    }

    /**
     * 正式发布模板（W0-4.4：发布门禁校验 + 生成版本快照）
     */
    @PreAuthorize("@ss.hasPermi('print:template:edit')")
    @Log(title = "打印模板", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/publish")
    public AjaxResult publish(@PathVariable("id") Long id, @RequestParam(required = false) String remark) {
        return success(printTemplateService.publish(id, remark));
    }

    /**
     * 回滚到历史版本（W0-4.4：生成新版本发布，不覆盖历史版本）
     */
    @PreAuthorize("@ss.hasPermi('print:template:edit')")
    @Log(title = "打印模板", businessType = BusinessType.UPDATE)
    @PutMapping("/{templateId}/rollback/{versionId}")
    public AjaxResult rollback(@PathVariable("templateId") Long templateId, @PathVariable("versionId") Long versionId,
                               @RequestParam(required = false) String remark) {
        return success(printTemplateService.rollback(templateId, versionId, remark));
    }

    /**
     * 查询模板版本列表（W0-4.4）
     */
    @PreAuthorize("@ss.hasPermi('print:template:list')")
    @GetMapping("/{id}/versions")
    public AjaxResult versions(@PathVariable("id") Long id) {
        return success(printTemplateService.listVersions(id));
    }

    /**
     * 记录一次打印预览（W0-4.4：正式打印前必须有预览记录）
     */
    @PreAuthorize("@ss.hasPermi('print:template:list')")
    @Log(title = "打印模板", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/preview")
    public AjaxResult preview(@PathVariable("id") Long id, @RequestParam(required = false) Long deliveryOrderId) {
        return toAjax(printTemplateService.recordPreview(id, deliveryOrderId));
    }
}

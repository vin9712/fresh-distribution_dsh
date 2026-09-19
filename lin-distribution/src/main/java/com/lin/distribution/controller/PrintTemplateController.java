package com.lin.distribution.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
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
     * 查询积木报表设计器可用报表清单（打印模板表单下拉选择，替代手工复制报表ID）
     */
    @PreAuthorize("@ss.hasPermi('print:template:list')")
    @GetMapping("/jimu-reports")
    public AjaxResult jimuReports() {
        return success(printTemplateService.selectJimuReports());
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

    /**
     * 查询打印数据契约（PR-D3）：字段字典 / 数据集 / 参数，供设计器字段面板与模板生成器共用
     */
    @Operation(summary = "查询打印数据契约")
    @PreAuthorize("@ss.hasPermi('print:template:list')")
    @GetMapping("/contract")
    public AjaxResult contract(@RequestParam(value = "form", required = false, defaultValue = "FLAT") String form,
                               @RequestParam(value = "rowsType", required = false, defaultValue = "LONG") String rowsType) {
        com.lin.distribution.service.support.PrintDataContract.Form f =
                com.lin.distribution.service.support.PrintDataContract.formOf(form);
        com.lin.distribution.service.support.PrintDataContract.RowsShape shape =
                com.lin.distribution.service.support.PrintDataContract.shapeOf(rowsType);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("form", f.name());
        resp.put("rowsType", shape.name());
        resp.put("fields", com.lin.distribution.service.support.PrintDataContract.fields(f, shape));
        resp.put("params", com.lin.distribution.service.support.PrintDataContract.params(f));
        List<Map<String, Object>> dataSets = new java.util.ArrayList<>();
        for (com.lin.distribution.service.support.PrintDataContract.DataSet ds
                : com.lin.distribution.service.support.PrintDataContract.dataSets(f, shape)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dbCode", ds.dbCode());
            item.put("chName", ds.chName());
            item.put("list", ds.list());
            item.put("fields", com.lin.distribution.service.support.PrintDataContract.fieldsOfDataSet(ds.dbCode(), shape));
            dataSets.add(item);
        }
        resp.put("dataSets", dataSets);
        return success(resp);
    }

    /**
     * 按数据契约重新物化模板接线（PR-D3，幂等）：对齐数据集 URL/转换器/参数 + 补齐打印回执钩子
     */
    @Operation(summary = "重新物化打印模板接线")
    @PreAuthorize("@ss.hasPermi('print:template:edit')")
    @Log(title = "打印模板", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/materialize")
    public AjaxResult materialize(@PathVariable("id") Long id) {
        return success(printTemplateService.materialize(id));
    }

    // ==================== W0-6 模板导入导出 ====================

    /**
     * 导出模板为开放 JSON 包（脱敏，可选携带历史版本）
     */
    @Operation(summary = "导出打印模板")
    @PreAuthorize("@ss.hasPermi('print:template:export')")
    @Log(title = "打印模板", businessType = BusinessType.EXPORT)
    @GetMapping("/{id}/export")
    public AjaxResult exportTemplate(@PathVariable("id") Long id,
                                    @RequestParam(defaultValue = "false") boolean includeVersions) {
        return success(printTemplateService.exportTemplate(id, includeVersions));
    }

    /**
     * 导入模板包（全有或全无安全校验、20MB 上限、禁止网络资源、导入后未绑定草稿并物化报表）
     *
     * <p>P1：已移除「静态样板 create 导入」分支——静态样板导入器退役，
     * 模板改由「骨架生成（P3）+ 设计器」产出，不再从静态 JSON 建报表。</p>
     */
    @Operation(summary = "导入打印模板")
    @PreAuthorize("@ss.hasPermi('print:template:edit')")
    @Log(title = "打印模板", businessType = BusinessType.IMPORT)
    @PostMapping("/import")
    public AjaxResult importTemplate(@RequestBody String packageJson) {
        return success(printTemplateService.importTemplates(packageJson));
    }

    @Autowired
    private com.lin.distribution.service.support.PrintTemplateGenerator printTemplateGenerator;

    /**
     * 生成打印模板骨架（P3 动态生成，PR-D4）：按形态+客户实际结构产出设计 JSON，
     * 替代「导入静态 JSON 样板」。生成结果为草稿内容，提交时由后端物化为报表。
     */
    @Operation(summary = "生成打印模板骨架")
    @PreAuthorize("@ss.hasPermi('print:template:edit')")
    @PostMapping("/generate")
    public AjaxResult generate(@RequestBody com.lin.distribution.dto.PrintTemplateGenerateDTO req) {
        return success(printTemplateGenerator.generate(req));
    }
}

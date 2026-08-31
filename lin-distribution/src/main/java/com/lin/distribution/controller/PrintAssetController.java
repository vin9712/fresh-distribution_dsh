package com.lin.distribution.controller;

import java.util.List;

import com.lin.distribution.domain.PrintAsset;
import com.lin.distribution.service.PrintAssetService;
import com.lin.distribution.service.support.TemplateContentGovernor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;

/**
 * 打印资源接口（W0-6：Logo/底图等本机文件目录资源治理）
 *
 * @author dsh
 */
@Tag(name = "打印资源接口")
@RestController
@RequestMapping("/print/asset")
public class PrintAssetController extends BaseController {

    @Autowired
    private PrintAssetService printAssetService;

    /** 资源列表 */
    @Operation(summary = "打印资源列表")
    @PreAuthorize("@ss.hasPermi('print:template:list')")
    @GetMapping("/list")
    public AjaxResult list(PrintAsset printAsset) {
        List<PrintAsset> list = printAssetService.selectPrintAssetList(printAsset);
        return success(list);
    }

    /** 资源详情 */
    @Operation(summary = "打印资源详情")
    @PreAuthorize("@ss.hasPermi('print:template:list')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(printAssetService.selectPrintAssetById(id));
    }

    /**
     * 上传资源（蓝图 36：PNG/JPG 5MB；37：本机文件目录与数据库同批备份）
     */
    @Operation(summary = "上传打印资源")
    @PreAuthorize("@ss.hasPermi('print:template:edit')")
    @Log(title = "打印资源", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam("file") MultipartFile file) {
        PrintAsset asset = printAssetService.upload(file);
        AjaxResult ok = success(asset);
        ok.put("maxSize", TemplateContentGovernor.ASSET_MAX_BYTES);
        ok.put("extensions", TemplateContentGovernor.ASSET_EXTENSIONS);
        return ok;
    }

    /**
     * 删除资源（被模板/历史版本引用则拒绝删除；蓝图 37：历史引用资源不可物理删除）
     */
    @Operation(summary = "删除打印资源")
    @PreAuthorize("@ss.hasPermi('print:template:edit')")
    @Log(title = "打印资源", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {
        return toAjax(printAssetService.deletePrintAssetById(id));
    }
}

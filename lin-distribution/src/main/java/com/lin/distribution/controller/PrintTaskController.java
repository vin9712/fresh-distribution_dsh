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
import com.lin.distribution.domain.PrintTask;
import com.lin.distribution.service.PrintTaskService;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.common.core.page.TableDataInfo;

/**
 * 打印任务Controller
 *
 * @author lin
 * @date 2025-01-07
 */
@Tag(name = "打印任务接口")
@RestController
@RequestMapping("/print/task")
public class PrintTaskController extends BaseController {
    @Autowired
    private PrintTaskService printTaskService;

    /**
     * 分页查询打印任务列表
     */
    @PreAuthorize("@ss.hasPermi('distribution:task:list')")
    @GetMapping("/page")
    public TableDataInfo page(PrintTask printTask) {
        startPage();
        List<PrintTask> list = printTaskService.selectPrintTaskList(printTask);
        return getDataTable(list);
    }

    /**
     * 查询打印任务列表
     */
    @PreAuthorize("@ss.hasPermi('distribution:task:list')")
    @GetMapping("/list")
    public AjaxResult list(PrintTask printTask) {
        List<PrintTask> list = printTaskService.selectPrintTaskList(printTask);
        return success(list);
    }

    /**
     * 导出打印任务列表
     */
    @PreAuthorize("@ss.hasPermi('distribution:task:export')")
    @Log(title = "打印任务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, PrintTask printTask) {
        List<PrintTask> list = printTaskService.selectPrintTaskList(printTask);
        ExcelUtil<PrintTask> util = new ExcelUtil<PrintTask>(PrintTask.class);
        util.exportExcel(response, list, "打印任务数据");
    }

    /**
     * 获取打印任务详细信息
     */
    @PreAuthorize("@ss.hasPermi('distribution:task:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(printTaskService.selectPrintTaskById(id));
    }

    /**
     * 新增打印任务
     */
    @PreAuthorize("@ss.hasPermi('distribution:task:add')")
    @Log(title = "打印任务", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody PrintTask printTask) {
        return toAjax(printTaskService.insertPrintTask(printTask));
    }

    /**
     * 修改打印任务
     */
    @PreAuthorize("@ss.hasPermi('distribution:task:edit')")
    @Log(title = "打印任务", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PrintTask printTask) {
        return toAjax(printTaskService.updatePrintTask(printTask));
    }

    /**
     * 删除打印任务
     */
    @PreAuthorize("@ss.hasPermi('distribution:task:remove')")
    @Log(title = "打印任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(printTaskService.deletePrintTaskByIds(ids));
    }
}
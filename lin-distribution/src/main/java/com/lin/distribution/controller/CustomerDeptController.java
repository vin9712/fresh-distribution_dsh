package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.core.page.TableDataInfo;
import com.lin.common.enums.BusinessType;
import com.lin.common.utils.ServletUtils;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.service.CustomerDeptService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 客户部门Controller
 *
 * @author lin
 * @date 2024-11-09
 */
@RestController
@RequestMapping("/partner/customerDept")
public class CustomerDeptController extends BaseController {
    @Autowired
    private CustomerDeptService customerDeptService;

    /**
     * 分页查询客户部门列表
     * 拓展查询参数：hideParent：是否隐藏父级部门，默认为 true 隐藏
     */
    @PreAuthorize("@ss.hasPermi('partner:customerDept:list')")
    @GetMapping("/page")
    public TableDataInfo page(CustomerDept customerDept) {
        // 获取请求参数，拓展查询逻辑
        Map<String, Object> paramMap = ServletUtils.getReqParamMap(ServletUtils.getRequest());
        customerDept.setParams(paramMap);

        startPage();
        List<CustomerDept> list = customerDeptService.selectCustomerDeptList(customerDept);
        return getDataTable(list);
    }

    /**
     * 查询客户部门列表
     */
    @PreAuthorize("@ss.hasPermi('partner:customerDept:list')")
    @GetMapping("/list")
    public AjaxResult list(CustomerDept customerDept) {
        List<CustomerDept> list = customerDeptService.selectCustomerDeptList(customerDept);
        return success(list);
    }

    /**
     * 导出客户部门列表
     */
    @PreAuthorize("@ss.hasPermi('partner:customerDept:export')")
    @Log(title = "客户部门", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, CustomerDept customerDept) {
        List<CustomerDept> list = customerDeptService.selectCustomerDeptList(customerDept);
        ExcelUtil<CustomerDept> util = new ExcelUtil<CustomerDept>(CustomerDept.class);
        util.exportExcel(response, list, "客户部门数据");
    }

    /**
     * 获取客户部门详细信息
     */
    @PreAuthorize("@ss.hasPermi('partner:customerDept:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(customerDeptService.selectCustomerDeptById(id));
    }

    /**
     * 新增客户部门
     */
    @PreAuthorize("@ss.hasPermi('partner:customerDept:add')")
    @Log(title = "客户部门", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CustomerDept customerDept) {
        return toAjax(customerDeptService.insertCustomerDept(customerDept));
    }

    /**
     * 修改客户部门
     */
    @PreAuthorize("@ss.hasPermi('partner:customerDept:edit')")
    @Log(title = "客户部门", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CustomerDept customerDept) {
        return toAjax(customerDeptService.updateCustomerDept(customerDept));
    }

    /**
     * 删除客户部门
     */
    @PreAuthorize("@ss.hasPermi('partner:customerDept:remove')")
    @Log(title = "客户部门", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(customerDeptService.deleteCustomerDeptByIds(ids));
    }
}

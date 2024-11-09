package com.lin.distribution.controller;

import java.util.List;

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
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.service.CustomerDeptService;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.common.core.page.TableDataInfo;

/**
 * 客户部门Controller
 *
 * @author lin
 * @date 2024-11-09
 */
@RestController
@RequestMapping("/partner/customer/dept")
public class CustomerDeptController extends BaseController {
    @Autowired
    private CustomerDeptService customerDeptService;

    /**
     * 查询客户部门列表
     */
    @PreAuthorize("@ss.hasPermi('partner:customerDept:list')")
    @GetMapping("/list")
    public TableDataInfo list(CustomerDept customerDept) {
        startPage();
        List<CustomerDept> list = customerDeptService.selectCustomerDeptList(customerDept);
        return getDataTable(list);
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

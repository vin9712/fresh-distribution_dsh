package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.core.page.TableDataInfo;
import com.lin.common.enums.BusinessType;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.service.CustomerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 客户管理Controller
 *
 * @author lin
 * @date 2024-11-09
 */
@Tag(name = "客户信息管理")
@RestController
@RequestMapping("/partner/customer" )
public class CustomerController extends BaseController {
    @Autowired
    private CustomerService customerService;

    /**
     * 分页查询客户管理列表
     */
    @PreAuthorize("@ss.hasPermi('partner:customer:list')" )
    @GetMapping("/page" )
    public TableDataInfo page(Customer customer) {
        startPage();
        List<Customer> list = customerService.selectCustomerList(customer);
        return getDataTable(list);
    }

    /**
     * 查询客户管理列表
     */
    @PreAuthorize("@ss.hasPermi('partner:customer:list')" )
    @GetMapping("/list" )
    public AjaxResult list(Customer customer) {
        List<Customer> list = customerService.selectCustomerList(customer);
        return success(list);
    }

    /**
     * 导出客户管理列表
     */
    @PreAuthorize("@ss.hasPermi('partner:customer:export')" )
    @Log(title = "客户管理" , businessType = BusinessType.EXPORT)
    @PostMapping("/export" )
    public void export(HttpServletResponse response, Customer customer) {
        List<Customer> list = customerService.selectCustomerList(customer);
        ExcelUtil<Customer> util = new ExcelUtil<Customer>(Customer.class);
        util.exportExcel(response, list, "客户管理数据" );
    }

    /**
     * 获取客户管理详细信息
     */
    @PreAuthorize("@ss.hasPermi('partner:customer:query')" )
    @GetMapping(value = "/{id}" )
    public AjaxResult getInfo(@PathVariable("id" ) Long id) {
        return success(customerService.selectCustomerById(id));
    }

    /**
     * 新增客户管理
     */
    @PreAuthorize("@ss.hasPermi('partner:customer:add')" )
    @Log(title = "客户管理" , businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Customer customer) {
        return toAjax(customerService.insertCustomer(customer));
    }

    /**
     * 修改客户管理
     */
    @PreAuthorize("@ss.hasPermi('partner:customer:edit')" )
    @Log(title = "客户管理" , businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Customer customer) {
        return toAjax(customerService.updateCustomer(customer));
    }

    /**
     * 删除客户管理
     */
    @PreAuthorize("@ss.hasPermi('partner:customer:remove')" )
    @Log(title = "客户管理" , businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}" )
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(customerService.deleteCustomerByIds(ids));
    }

    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file) throws Exception {
        ExcelUtil<Customer> util = new ExcelUtil<>(Customer.class);
        List<Customer> customerList = util.importExcel(file.getInputStream());
        String message = customerService.importCustomer(customerList);
        return AjaxResult.success(message);
    }

    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil<Customer> util = new ExcelUtil<>(Customer.class);
        util.importTemplateExcel(response, "客户数据");
    }
}

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
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.service.SaleOrderDetailService;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.common.core.page.TableDataInfo;

/**
 * 销售订单详情Controller
 *
 * @author lin
 * @date 2024-11-23
 */
@Tag(name = "销售订单详情接口")
@RestController
@RequestMapping("/order/saleDetail")
public class SaleOrderDetailController extends BaseController {
    @Autowired
    private SaleOrderDetailService saleOrderDetailService;

    /**
     * 分页查询销售订单详情列表
     */
    @PreAuthorize("@ss.hasPermi('order:saleDetail:list')")
    @GetMapping("/page")
    public TableDataInfo page(SaleOrderDetail saleOrderDetail) {
        startPage();
        List<SaleOrderDetail> list = saleOrderDetailService.selectSaleOrderDetailList(saleOrderDetail);
        return getDataTable(list);
    }

    /**
     * 查询销售订单详情列表
     */
    @PreAuthorize("@ss.hasPermi('order:saleDetail:list')")
    @GetMapping("/list")
    public AjaxResult list(SaleOrderDetail saleOrderDetail) {
        List<SaleOrderDetail> list = saleOrderDetailService.selectSaleOrderDetailList(saleOrderDetail);
        return success(list);
    }

    /**
     * 导出销售订单详情列表
     */
    @PreAuthorize("@ss.hasPermi('order:saleDetail:export')")
    @Log(title = "销售订单详情", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SaleOrderDetail saleOrderDetail) {
        List<SaleOrderDetail> list = saleOrderDetailService.selectSaleOrderDetailList(saleOrderDetail);
        ExcelUtil<SaleOrderDetail> util = new ExcelUtil<SaleOrderDetail>(SaleOrderDetail.class);
        util.exportExcel(response, list, "销售订单详情数据");
    }

    /**
     * 获取销售订单详情详细信息
     */
    @PreAuthorize("@ss.hasPermi('order:saleDetail:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(saleOrderDetailService.selectSaleOrderDetailById(id));
    }

    /**
     * 新增销售订单详情
     */
    @PreAuthorize("@ss.hasPermi('order:saleDetail:add')")
    @Log(title = "销售订单详情", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SaleOrderDetail saleOrderDetail) {
        return toAjax(saleOrderDetailService.insertSaleOrderDetail(saleOrderDetail));
    }

    /**
     * 修改销售订单详情
     */
    @PreAuthorize("@ss.hasPermi('order:saleDetail:edit')")
    @Log(title = "销售订单详情", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SaleOrderDetail saleOrderDetail) {
        return toAjax(saleOrderDetailService.updateSaleOrderDetail(saleOrderDetail));
    }

    /**
     * 删除销售订单详情
     */
    @PreAuthorize("@ss.hasPermi('order:saleDetail:remove')")
    @Log(title = "销售订单详情", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(saleOrderDetailService.deleteSaleOrderDetailByIds(ids));
    }
}

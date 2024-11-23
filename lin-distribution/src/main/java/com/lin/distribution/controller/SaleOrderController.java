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
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.service.SaleOrderService;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.common.core.page.TableDataInfo;

/**
 * 销售订单Controller
 *
 * @author lin
 * @date 2024-11-23
 */
@Tag(name = "销售订单接口")
@RestController
@RequestMapping("/order/sale")
public class SaleOrderController extends BaseController {
    @Autowired
    private SaleOrderService saleOrderService;

    /**
     * 分页查询销售订单列表
     */
    @PreAuthorize("@ss.hasPermi('order:sale:list')")
    @GetMapping("/page")
    public TableDataInfo page(SaleOrder saleOrder) {
        startPage();
        List<SaleOrder> list = saleOrderService.selectSaleOrderList(saleOrder);
        return getDataTable(list);
    }

    /**
     * 查询销售订单列表
     */
    @PreAuthorize("@ss.hasPermi('order:sale:list')")
    @GetMapping("/list")
    public AjaxResult list(SaleOrder saleOrder) {
        List<SaleOrder> list = saleOrderService.selectSaleOrderList(saleOrder);
        return success(list);
    }

    /**
     * 导出销售订单列表
     */
    @PreAuthorize("@ss.hasPermi('order:sale:export')")
    @Log(title = "销售订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SaleOrder saleOrder) {
        List<SaleOrder> list = saleOrderService.selectSaleOrderList(saleOrder);
        ExcelUtil<SaleOrder> util = new ExcelUtil<SaleOrder>(SaleOrder.class);
        util.exportExcel(response, list, "销售订单数据");
    }

    /**
     * 获取销售订单详细信息
     */
    @PreAuthorize("@ss.hasPermi('order:sale:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(saleOrderService.selectSaleOrderById(id));
    }

    /**
     * 新增销售订单
     */
    @PreAuthorize("@ss.hasPermi('order:sale:add')")
    @Log(title = "销售订单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SaleOrder saleOrder) {
        return toAjax(saleOrderService.insertSaleOrder(saleOrder));
    }

    /**
     * 修改销售订单
     */
    @PreAuthorize("@ss.hasPermi('order:sale:edit')")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SaleOrder saleOrder) {
        return toAjax(saleOrderService.updateSaleOrder(saleOrder));
    }

    /**
     * 删除销售订单
     */
    @PreAuthorize("@ss.hasPermi('order:sale:remove')")
    @Log(title = "销售订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(saleOrderService.deleteSaleOrderByIds(ids));
    }
}

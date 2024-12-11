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
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.service.DeliveryOrderDetailService;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.common.core.page.TableDataInfo;

/**
 * 送货单详情Controller
 *
 * @author lin
 * @date 2024-12-11
 */
@Tag(name = "送货单详情接口")
@RestController
@RequestMapping("/order/delivery")
public class DeliveryOrderDetailController extends BaseController {
    @Autowired
    private DeliveryOrderDetailService deliveryOrderDetailService;

    /**
     * 分页查询送货单详情列表
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:list')")
    @GetMapping("/page")
    public TableDataInfo page(DeliveryOrderDetail deliveryOrderDetail) {
        startPage();
        List<DeliveryOrderDetail> list = deliveryOrderDetailService.selectDeliveryOrderDetailList(deliveryOrderDetail);
        return getDataTable(list);
    }

    /**
     * 查询送货单详情列表
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:list')")
    @GetMapping("/list")
    public AjaxResult list(DeliveryOrderDetail deliveryOrderDetail) {
        List<DeliveryOrderDetail> list = deliveryOrderDetailService.selectDeliveryOrderDetailList(deliveryOrderDetail);
        return success(list);
    }

    /**
     * 导出送货单详情列表
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:export')")
    @Log(title = "送货单详情", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, DeliveryOrderDetail deliveryOrderDetail) {
        List<DeliveryOrderDetail> list = deliveryOrderDetailService.selectDeliveryOrderDetailList(deliveryOrderDetail);
        ExcelUtil<DeliveryOrderDetail> util = new ExcelUtil<DeliveryOrderDetail>(DeliveryOrderDetail.class);
        util.exportExcel(response, list, "送货单详情数据");
    }

    /**
     * 获取送货单详情详细信息
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(deliveryOrderDetailService.selectDeliveryOrderDetailById(id));
    }

    /**
     * 新增送货单详情
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:add')")
    @Log(title = "送货单详情", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DeliveryOrderDetail deliveryOrderDetail) {
        return toAjax(deliveryOrderDetailService.insertDeliveryOrderDetail(deliveryOrderDetail));
    }

    /**
     * 修改送货单详情
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:edit')")
    @Log(title = "送货单详情", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DeliveryOrderDetail deliveryOrderDetail) {
        return toAjax(deliveryOrderDetailService.updateDeliveryOrderDetail(deliveryOrderDetail));
    }

    /**
     * 删除送货单详情
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:remove')")
    @Log(title = "送货单详情", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(deliveryOrderDetailService.deleteDeliveryOrderDetailByIds(ids));
    }
}

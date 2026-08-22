package com.lin.distribution.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.dto.DeliveryByOrdersDTO;
import com.lin.distribution.service.DeliveryOrderService;
import com.lin.distribution.service.PrintTemplateService;
import com.lin.common.utils.poi.ExcelUtil;
import com.lin.common.core.page.TableDataInfo;

/**
 * 送货单据Controller
 *
 * @author lin
 * @date 2024-12-11
 */
@Tag(name = "送货单据接口")
@RestController
@RequestMapping("/order/delivery")
public class DeliveryOrderController extends BaseController {
    @Autowired
    private DeliveryOrderService deliveryOrderService;
    @Autowired
    private PrintTemplateService printTemplateService;

    /**
     * 分页查询送货单据列表
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:list')")
    @GetMapping("/page")
    public TableDataInfo page(DeliveryOrder deliveryOrder) {
        startPage();
        List<DeliveryOrder> list = deliveryOrderService.selectDeliveryOrderList(deliveryOrder);
        return getDataTable(list);
    }

    /**
     * 查询送货单据列表
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:list')")
    @GetMapping("/list")
    public AjaxResult list(DeliveryOrder deliveryOrder) {
        List<DeliveryOrder> list = deliveryOrderService.selectDeliveryOrderList(deliveryOrder);
        return success(list);
    }

    /**
     * 导出送货单据列表
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:export')")
    @Log(title = "送货单据", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, DeliveryOrder deliveryOrder) {
        List<DeliveryOrder> list = deliveryOrderService.selectDeliveryOrderList(deliveryOrder);
        ExcelUtil<DeliveryOrder> util = new ExcelUtil<DeliveryOrder>(DeliveryOrder.class);
        util.exportExcel(response, list, "送货单据数据");
    }

    /**
     * 获取送货单据详细信息
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(deliveryOrderService.selectDeliveryOrderById(id));
    }

    /**
     * 获取送货单明细列表（按商品合并行）
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:query')")
    @GetMapping(value = "/{id}/detail")
    public AjaxResult detail(@PathVariable("id") Long id) {
        return success(deliveryOrderService.selectDetailListByDeliveryId(id));
    }

    /**
     * 打印信息：三级绑定解析模板 + 联数（打印计数由 /{id}/print 记录）
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @GetMapping(value = "/{id}/printInfo")
    public AjaxResult printInfo(@PathVariable("id") Long id) {
        DeliveryOrder deliveryOrder = deliveryOrderService.selectDeliveryOrderById(id);
        if (deliveryOrder == null) {
            return error("送货单不存在");
        }
        com.lin.distribution.domain.PrintTemplate template = printTemplateService.resolveForDeliveryOrder(deliveryOrder);
        Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("deliveryOrderId", id);
        info.put("code", deliveryOrder.getCode());
        info.put("templateId", template.getContent());
        info.put("templateName", template.getName());
        info.put("copies", template.getCopies() == null ? 1 : template.getCopies());
        return success(info);
    }

    /**
     * 新增送货单据
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:add')")
    @Log(title = "送货单据", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DeliveryOrder deliveryOrder) {
        return toAjax(deliveryOrderService.insertDeliveryOrder(deliveryOrder));
    }

    /**
     * 修改送货单据
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:edit')")
    @Log(title = "送货单据", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DeliveryOrder deliveryOrder) {
        return toAjax(deliveryOrderService.updateDeliveryOrder(deliveryOrder));
    }

    /**
     * 删除送货单据
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:remove')")
    @Log(title = "送货单据", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(deliveryOrderService.deleteDeliveryOrderByIds(ids));
    }

    /**
     * 按配送日期生成送货单（DESIGN.md §7.2，仅汇总已确认订单，按客户+配送点分组、明细按商品合并）
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:add')")
    @Log(title = "送货单生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate/{deliveryDate}")
    public AjaxResult generate(@PathVariable("deliveryDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate) {
        return success(deliveryOrderService.generateByDeliveryDate(deliveryDate));
    }

    /**
     * 按勾选订单生成送货单（销售订单列表页抽屉，配送日期可调整）
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:add')")
    @Log(title = "送货单生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate-by-orders")
    public AjaxResult generateByOrders(@RequestBody @Validated DeliveryByOrdersDTO dto) {
        return success(deliveryOrderService.generateByOrderIds(dto));
    }

    /**
     * 标记打印：print_count + 1，状态 → 已打印
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @Log(title = "送货单打印", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/print")
    public AjaxResult print(@PathVariable("id") Long id) {
        return success(deliveryOrderService.markPrinted(id));
    }

    /**
     * 标记送达：状态 → 已送达，同组已确认订单 → DELIVERED
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:deliver')")
    @Log(title = "送货单送达", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/deliver")
    public AjaxResult deliver(@PathVariable("id") Long id) {
        return success(deliveryOrderService.markDelivered(id));
    }
}

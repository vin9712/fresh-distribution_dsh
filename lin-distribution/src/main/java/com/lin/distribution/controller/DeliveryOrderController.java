package com.lin.distribution.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestParam;
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
import com.lin.distribution.dto.DeliveryNoPrintDTO;
import com.lin.distribution.dto.DeliveryVoidDTO;
import com.lin.distribution.service.DeliveryBatchService;
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
    private DeliveryBatchService deliveryBatchService;
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
     * 批次分组聚合分页（D-043 送货单据页：主行=客户+配送日期=批次，聚合张数/状态数/合计/打印形态/提醒最高级）
     *
     * <p>必须后端分组——前端分页会切断同一批次。子行（单号/点/状态/打印次数）由前端展开时
     * 按 customerId+deliveryDate 调既有 {@link #list(DeliveryOrder)}。</p>
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:list')")
    @GetMapping("/batch-page")
    public TableDataInfo batchPage(DeliveryOrder deliveryOrder) {
        startPage();
        List<com.lin.distribution.vo.DeliveryBatchPageVO> list = deliveryOrderService.selectBatchPage(deliveryOrder);
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
     * 来源视图（S14 §6.1/§八）：聚合行 + 展开的来源订单/行/分配量（历史单 sources 为空）
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:query')")
    @GetMapping(value = "/{id}/sources")
    public AjaxResult sources(@PathVariable("id") Long id) {
        return success(deliveryOrderService.selectDeliverySources(id));
    }

    /**
     * 客户日总表（S14 §6.1/§八，D-027/28）：标准品名+总量+各配送点小计，
     * 无价格、不因价格拆行；内部配货/采购视图，历史单回退明细行聚合。
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:batch')")
    @GetMapping("/batch/view")
    public AjaxResult batchView(@RequestParam("customerId") Long customerId,
                                @RequestParam("date") String date) {
        return success(deliveryBatchService.selectBatchView(customerId, date));
    }

    /**
     * 点单视图（D-055：客户+日期+配送点 的订单明细行，含加单/换货/退货标记）——客户日总表页「按配送点查看」口径
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:batch')")
    @GetMapping("/batch/point-view")
    public AjaxResult pointView(@RequestParam("customerId") Long customerId,
                                @RequestParam("deptId") Long deptId,
                                @RequestParam("date") String date) {
        return success(deliveryBatchService.selectPointView(customerId, deptId, date));
    }

    /**
     * 点单全点视图（D-055 收尾）：按 客户+日期 返回当天实际有单的配送点分组（组内=订单明细含标记），
     * 客户日总表页点单口径据分 tab 展示，不再需要配送点选择器。
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:batch')")
    @GetMapping("/batch/point-view-all")
    public AjaxResult pointViewAll(@RequestParam("customerId") Long customerId,
                                   @RequestParam("date") String date) {
        return success(deliveryBatchService.selectPointViewAll(customerId, date));
    }

    /**
     * 矩阵总表（D-044/D-047/D-051）：行=送货明细行（不同价必拆行）、列=配送点快照（含当日无单空列）、
     * 格=source_item 分配量透视；纸面不打单价与金额，同名多行以 (档①) 标记区分（D-046）。
     * 页面与打印共用本接口，附恒等式自检结果（D-047）。
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:batch')")
    @GetMapping("/batch/{customerId}/{deliveryDate}/matrix")
    public AjaxResult matrix(@PathVariable("customerId") Long customerId,
                             @PathVariable("deliveryDate") String deliveryDate) {
        return success(deliveryBatchService.selectMatrix(customerId, deliveryDate));
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
        // 模板主键（预览记录 /print/template/{id}/preview 入参）与 JimuReport 报表 ID 分开返回
        info.put("templateRecordId", template.getId());
        info.put("templateId", template.getContent());
        info.put("templateName", template.getName());
        info.put("copies", template.getCopies() == null ? 1 : template.getCopies());
        // 供前端筛选「本次生效」可切换的已发布模板（三级绑定同口径）
        info.put("customerId", deliveryOrder.getCustomerId());
        info.put("deliveryPointId", deliveryOrder.getDeliveryPointId());
        return success(info);
    }

    /**
     * 候选打印模板（P1/D-048 替代前端复刻过滤）：同印刷形态、该客户可用的已发布模板，
     * 按绑定层级排序 + 命中「全局默认」告警标记。
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @GetMapping(value = "/{id}/print-candidates")
    public AjaxResult printCandidates(@PathVariable("id") Long id) {
        DeliveryOrder deliveryOrder = deliveryOrderService.selectDeliveryOrderById(id);
        if (deliveryOrder == null) {
            return error("送货单不存在");
        }
        return success(printTemplateService.selectPrintCandidates(deliveryOrder));
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
     * 标记打印：print_count + 1，状态 → 已打印
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @Log(title = "送货单打印", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/print")
    public AjaxResult print(@PathVariable("id") Long id) {
        return success(deliveryOrderService.markPrinted(id));
    }

    /**
     * 标记送达：状态 → 已送达，同组已确认订单 → DELIVERED（兼容旧入口：
     * 未打印单送达会被拒绝，请改用 /{id}/delivered 并携带免纸原因）
     */
    @Deprecated
    @PreAuthorize("@ss.hasPermi('order:delivery:deliver')")
    @Log(title = "送货单送达", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/deliver")
    public AjaxResult deliver(@PathVariable("id") Long id) {
        return success(deliveryOrderService.markDelivered(id));
    }

    /**
     * 标记送达（S14/T4）：状态 → 已送达，仅回写来源台账命中的订单；
     * 未打印（PENDING）送达时 body 必须携带免纸原因 {noPrint: {reasonCode, remark}}（D-018）
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:deliver')")
    @Log(title = "送货单送达", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/delivered")
    public AjaxResult delivered(@PathVariable("id") Long id,
                                @RequestBody(required = false) DeliveryNoPrintDTO noPrint) {
        return success(deliveryOrderService.markDelivered(id, noPrint));
    }

    /**
     * 作废送货单（S14/T4，仅适用 D-055 前的历史单证）：PENDING/PRINTED 可作废（原因必填），
     * 来源分配软删释放订单；已提交验收或来源订单已结算时拒绝。
     * D-055 视图化后新日期不再生成送货单，本接口仅用于历史单据处理。
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:void')")
    @Log(title = "送货单作废", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/void")
    public AjaxResult voidOrder(@PathVariable("id") Long id,
                                @RequestBody @Validated DeliveryVoidDTO dto) {
        deliveryOrderService.voidDeliveryOrder(id, dto.getReasonCode(), dto.getReasonNote());
        return success();
    }

    /**
     * 打印分界登记（D-055）：前端每次打开打印视图后调一次。
     * 已打印 = 配送后，后续变更需走带标记的配送后变更（加单/换货/退货）。
     *
     * @param body {customerId, deliveryDate, customerDeptId?, templateId?}
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:print')")
    @Log(title = "送货打印登记", businessType = BusinessType.INSERT)
    @PostMapping("/print-log")
    public AjaxResult printLog(@RequestBody Map<String, Object> body) {
        Long customerId = toLong(body.get("customerId"));
        String deliveryDate = toStr(body.get("deliveryDate"));
        Long customerDeptId = toLong(body.get("customerDeptId"));
        Long templateId = toLong(body.get("templateId"));
        return success(deliveryBatchService.markPrinted(customerId, deliveryDate, customerDeptId, templateId));
    }

    /**
     * 打印分界查询（D-055）：该 客户+日期(+配送点) 是否已打印。
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:batch')")
    @GetMapping("/print-state")
    public AjaxResult printState(@RequestParam("customerId") Long customerId,
                                 @RequestParam("deliveryDate") String deliveryDate,
                                 @RequestParam(value = "customerDeptId", required = false) Long customerDeptId) {
        AjaxResult result = AjaxResult.success();
        result.put("printed", deliveryBatchService.isPrinted(customerId, deliveryDate, customerDeptId));
        return result;
    }

    private Long toLong(Object value) {
        if (value == null || value.toString().trim().isEmpty()) {
            return null;
        }
        return Long.valueOf(value.toString().trim());
    }

    private String toStr(Object value) {
        return value == null ? null : value.toString().trim();
    }
}

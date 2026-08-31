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
import com.lin.distribution.constant.DeliveryGenerateTrigger;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.dto.DeliveryByOrdersDTO;
import com.lin.distribution.dto.DeliveryNoPrintDTO;
import com.lin.distribution.dto.DeliveryVoidDTO;
import com.lin.distribution.service.DeliveryBatchService;
import com.lin.distribution.service.DeliveryGenerationService;
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
    private DeliveryGenerationService deliveryGenerationService;
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
     * 按配送日期生成送货单（旧入口兼容：内部委托统一生成服务 generateForDate(MANUAL)，
     * D-025 语义=幂等补齐当日全部遗漏订单，不再整体拒绝重复生成）
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:add')")
    @Log(title = "送货单生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate/{deliveryDate}")
    public AjaxResult generate(@PathVariable("deliveryDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate) {
        return success(deliveryGenerationService.generateForDate(deliveryDate, DeliveryGenerateTrigger.MANUAL));
    }

    /**
     * 按勾选订单生成送货单（销售订单列表页抽屉 / 录单页「选订单·生成送货单」抽屉，D-025 语义
     * =按选中订单定位 客户+配送日期，由统一生成服务补齐对应客户当日的全部遗漏订单，已进单订单幂等排除）。
     * <p>confirmDrafts=true 时，勾选里的草稿会在同一事务内先批量确认再出单（录单页抽屉专用）。
     * <p>权限：列表页入口沿用 {@code order:delivery:add}，录单页抽屉入口用
     * {@code order:delivery:generateCustomer}（与前端 v-hasPermi 对齐，避免按钮可见但接口 403）。
     */
    @PreAuthorize("@ss.hasAnyPermi('order:delivery:add,order:delivery:generateCustomer')")
    @Log(title = "送货单生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate-by-orders")
    public AjaxResult generateByOrders(@RequestBody @Validated DeliveryByOrdersDTO dto) {
        return success(deliveryGenerationService.generateForOrders(dto));
    }

    /**
     * 按客户+配送日期手工生成/补单（S14/T3 统一生成服务主入口，D-021；
     * 供录单页「本客户订单已录完」按钮与客户维度补生成调用，幂等可重复触发）
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:add')")
    @Log(title = "送货单生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate/customer/{customerId}/{deliveryDate}")
    public AjaxResult generateForCustomer(@PathVariable("customerId") Long customerId,
                                          @PathVariable("deliveryDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate) {
        return success(deliveryGenerationService.generateForCustomer(customerId, deliveryDate));
    }

    /**
     * 生成前预览「待生成清单」（客户维度：一行=一个客户，展开看该客户待并入订单与既有单）。
     *
     * <p>与统一生成服务同源判定（遗漏订单/三态分支/组单策略快照/明细合并），只读不落库；
     * 送货单页「生成 → 预览 → 确认」与客户管理页「送货单」抽屉共用。customerId 传空=当日全部客户。</p>
     */
    @PreAuthorize("@ss.hasAnyPermi('order:delivery:add,order:delivery:generateCustomer,order:delivery:list')")
    @GetMapping("/group-preview")
    public AjaxResult groupPreview(@RequestParam("deliveryDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
                                   @RequestParam(value = "customerId", required = false) Long customerId) {
        return success(deliveryGenerationService.previewGenerate(deliveryDate, customerId));
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
     * 作废送货单（S14/T4，DESIGN.md §5.2）：PENDING/PRINTED 可作废（原因必填，新号重建），
     * 来源分配软删释放订单；已提交验收或来源订单已结算时拒绝。
     * 作废后重建/补充单走 POST /generate/customer/{customerId}/{deliveryDate}（三态自动分支）。
     */
    @PreAuthorize("@ss.hasPermi('order:delivery:void')")
    @Log(title = "送货单作废", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/void")
    public AjaxResult voidOrder(@PathVariable("id") Long id,
                                @RequestBody @Validated DeliveryVoidDTO dto) {
        deliveryOrderService.voidDeliveryOrder(id, dto.getReasonCode(), dto.getReasonNote());
        return success();
    }
}

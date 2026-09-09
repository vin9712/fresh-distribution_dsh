package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.core.page.TableDataInfo;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.dto.AcceptanceCreateDTO;
import com.lin.distribution.dto.AcceptanceQuickAcceptDTO;
import com.lin.distribution.dto.AcceptanceRevokeDTO;
import com.lin.distribution.dto.AcceptanceUpdateDTO;
import com.lin.distribution.service.AcceptanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 验收单Controller（DESIGN.md §9：一单一验、实收损耗、订单 ACCEPTED）
 *
 * @author dsh
 */
@Tag(name = "验收单管理")
@RestController
@RequestMapping("/acceptance")
public class AcceptanceController extends BaseController {
    @Autowired
    private AcceptanceService acceptanceService;

    /**
     * 分页查询验收单列表
     */
    @Operation(summary = "分页查询验收单列表")
    @PreAuthorize("@ss.hasPermi('acceptance:list')")
    @GetMapping("/page")
    public TableDataInfo page(Acceptance acceptance) {
        startPage();
        List<Acceptance> list = acceptanceService.selectAcceptanceList(acceptance);
        return getDataTable(list);
    }

    /**
     * 查询验收单列表
     */
    @Operation(summary = "查询验收单列表")
    @PreAuthorize("@ss.hasPermi('acceptance:list')")
    @GetMapping("/list")
    public AjaxResult list(Acceptance acceptance) {
        return success(acceptanceService.selectAcceptanceList(acceptance));
    }

    /**
     * 获取验收单详细信息
     */
    @Operation(summary = "获取验收单详细信息")
    @PreAuthorize("@ss.hasPermi('acceptance:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(acceptanceService.selectAcceptanceById(id));
    }

    /**
     * 获取验收单明细列表
     */
    @Operation(summary = "获取验收单明细列表")
    @PreAuthorize("@ss.hasPermi('acceptance:query')")
    @GetMapping(value = "/{id}/items")
    public AjaxResult items(@PathVariable("id") Long id) {
        return success(acceptanceService.selectItemListByAcceptanceId(id));
    }

    /**
     * 「去验收」定位（OA 订单维度优先）：订单列表已配送行跳转验收，
     * 订单维度命中/可建单时返回 orderView=true；历史单回退送货单反查链路
     */
    @Operation(summary = "按订单定位验收单")
    @PreAuthorize("@ss.hasPermi('acceptance:query')")
    @GetMapping("/by-order/{orderId}")
    public AjaxResult byOrder(@PathVariable("orderId") Long orderId) {
        return success(acceptanceService.locateBySaleOrder(orderId));
    }

    /**
     * 按送货单生成验收单草稿（一单一验）。
     *
     * @deprecated 历史单专用（AC-4）：新流程唯一建单入口为 create-by-customer-date，
     *             本接口仅为历史 status=2 订单补建验收保留
     */
    @Deprecated
    @Operation(summary = "按送货单生成验收单（历史单专用）")
    @PreAuthorize("@ss.hasPermi('acceptance:add')")
    @Log(title = "验收单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AcceptanceCreateDTO dto) {
        return success(acceptanceService.createByDeliveryOrder(dto.getDeliveryOrderId()));
    }

    /**
     * 按「客户+配送日期」生成验收单草稿（AC-1，《验收模块订单明细视角重构设计》）。
     *
     * @deprecated 已被 OA 订单维度验收取代（《订单页一键验收链路设计》§七，Q2 确认 2026-09-09：
     *             验收逐单进行，主入口=订单页「去验收」）；本接口无前端调用方，仅保留过渡期兼容
     * @param body {customerId, deliveryDate}
     */
    @Deprecated
    @Operation(summary = "按客户+配送日期生成验收单（已废弃：请用订单页「去验收」）")
    @PreAuthorize("@ss.hasPermi('acceptance:add')")
    @Log(title = "验收单", businessType = BusinessType.INSERT)
    @PostMapping("/create-by-customer-date")
    public AjaxResult createByCustomerDate(@RequestBody java.util.Map<String, Object> body) {
        Long customerId = body.get("customerId") == null ? null : Long.valueOf(String.valueOf(body.get("customerId")));
        LocalDate deliveryDate = body.get("deliveryDate") == null ? null
                : LocalDate.parse(String.valueOf(body.get("deliveryDate")).substring(0, 10));
        return success(acceptanceService.createByCustomerDate(customerId, deliveryDate));
    }

    /**
     * OA：按订单生成（或同步）验收草稿（《订单页一键验收链路设计》§4.3）：
     * 一订单一验；无单建草稿，已有草稿则幂等同步缺失行（验收中途加单/换货/退货）。
     *
     * @param body {orderId}
     */
    @Operation(summary = "按订单生成/同步验收草稿")
    @PreAuthorize("@ss.hasPermi('acceptance:add')")
    @Log(title = "验收单", businessType = BusinessType.INSERT)
    @PostMapping("/create-by-order")
    public AjaxResult createByOrder(@RequestBody java.util.Map<String, Object> body) {
        Long orderId = body.get("orderId") == null ? null : Long.valueOf(String.valueOf(body.get("orderId")));
        return success(acceptanceService.createByOrder(orderId));
    }

    /**
     * OA：订单一键验收（《订单页一键验收链路设计》§4.5）：
     * 建单（如无）→ 同步缺失行 → 应用实收覆盖（可选）→ 提交，原子完成；
     * 订单 → 已验收，回写 actual_* 镜像。
     */
    @Operation(summary = "订单一键验收")
    @PreAuthorize("@ss.hasPermi('acceptance:submit')")
    @Log(title = "验收单一键验收", businessType = BusinessType.UPDATE)
    @PostMapping("/quick-accept")
    public AjaxResult quickAccept(@RequestBody AcceptanceQuickAcceptDTO dto) {
        return success(acceptanceService.quickAccept(dto));
    }

    /**
     * 录入/修改验收单（仅草稿；实收金额与损耗后端重算）
     */
    @Operation(summary = "录入/修改验收单")
    @PreAuthorize("@ss.hasPermi('acceptance:edit')")
    @Log(title = "验收单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AcceptanceUpdateDTO dto) {
        return success(acceptanceService.updateDraft(dto));
    }

    /**
     * 提交验收单：来源订单 → ACCEPTED，同步 actual_* 镜像
     */
    @Operation(summary = "提交验收单")
    @PreAuthorize("@ss.hasPermi('acceptance:submit')")
    @Log(title = "验收单", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/submit")
    public AjaxResult submit(@PathVariable("id") Long id) {
        return success(acceptanceService.submit(id));
    }

    /**
     * 撤销验收（S14/T5）：已提交→草稿，原因必填，撤回前完整快照落审计；
     * 来源订单回退已配送；任一来源订单已结算时拒绝
     */
    @Operation(summary = "撤销验收单")
    @PreAuthorize("@ss.hasPermi('acceptance:revoke')")
    @Log(title = "验收单撤销", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/revoke")
    public AjaxResult revoke(@PathVariable("id") Long id,
                             @RequestBody @Validated AcceptanceRevokeDTO dto) {
        return success(acceptanceService.revoke(id, dto.getReason()));
    }

    /**
     * 删除验收单（仅草稿）
     */
    @Operation(summary = "删除验收单")
    @PreAuthorize("@ss.hasPermi('acceptance:remove')")
    @Log(title = "验收单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(acceptanceService.deleteByIds(ids));
    }
}

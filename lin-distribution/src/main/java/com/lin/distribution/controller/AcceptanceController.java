package com.lin.distribution.controller;

import com.lin.common.annotation.Log;
import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.core.page.TableDataInfo;
import com.lin.common.enums.BusinessType;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.dto.AcceptanceCreateDTO;
import com.lin.distribution.dto.AcceptanceUpdateDTO;
import com.lin.distribution.service.AcceptanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * 按送货单生成验收单草稿（一单一验）
     */
    @Operation(summary = "按送货单生成验收单")
    @PreAuthorize("@ss.hasPermi('acceptance:add')")
    @Log(title = "验收单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AcceptanceCreateDTO dto) {
        return success(acceptanceService.createByDeliveryOrder(dto.getDeliveryOrderId()));
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
     * 提交验收单：同组订单 → ACCEPTED
     */
    @Operation(summary = "提交验收单")
    @PreAuthorize("@ss.hasPermi('acceptance:submit')")
    @Log(title = "验收单", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/submit")
    public AjaxResult submit(@PathVariable("id") Long id) {
        return success(acceptanceService.submit(id));
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

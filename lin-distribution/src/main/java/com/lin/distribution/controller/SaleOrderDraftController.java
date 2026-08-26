package com.lin.distribution.controller;

import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.distribution.domain.SaleOrderDraft;
import com.lin.distribution.service.SaleOrderDraftService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 销售订单录入草稿Controller
 * 录单页草稿自动保存：后端为主、localStorage 兜底断网场景（录单页交互细化设计 §3.3）
 *
 * @author dsh
 */
@Tag(name = "销售订单录入草稿接口")
@RestController
@RequestMapping("/order/saleDraft")
@RequiredArgsConstructor
public class SaleOrderDraftController extends BaseController {

    private final SaleOrderDraftService saleOrderDraftService;

    /**
     * 按草稿键读取草稿
     *
     * @param draftKey 草稿键（new:{customerDeptId} / order:{orderId}）
     */
    @GetMapping("/{draftKey}")
    public AjaxResult get(@PathVariable("draftKey") String draftKey) {
        SaleOrderDraft draft = saleOrderDraftService.selectByDraftKey(draftKey);
        if (draft == null) {
            return success();
        }
        return success(Map.of(
                "draftKey", draft.getDraftKey(),
                "payload", draft.getPayload() == null ? "" : draft.getPayload(),
                "updateTime", draft.getUpdateTime()
        ));
    }

    /**
     * 保存（幂等覆盖）草稿
     */
    @PostMapping
    public AjaxResult save(@RequestBody Map<String, String> body) {
        String draftKey = body.get("draftKey");
        String payload = body.get("payload");
        return toAjax(saleOrderDraftService.saveDraft(draftKey, payload));
    }

    /**
     * 删除草稿
     *
     * @param draftKey 草稿键
     */
    @DeleteMapping("/{draftKey}")
    public AjaxResult remove(@PathVariable("draftKey") String draftKey) {
        // 幂等删除：草稿不存在（影响 0 行）也视为成功，避免空删返回“操作失败”
        saleOrderDraftService.deleteDraft(draftKey);
        return success();
    }
}

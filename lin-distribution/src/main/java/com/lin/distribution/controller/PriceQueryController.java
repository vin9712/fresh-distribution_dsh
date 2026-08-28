package com.lin.distribution.controller;

import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.distribution.service.PriceQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 取价接口（前端只展示取价结果，不实现优先级）
 * 价格层级简化后仅使用客户正式报价；未命中由文员手工定价。
 * deliveryPointId 参数保留仅为接口兼容，服务端已忽略。
 *
 * @author dsh
 */
@Tag(name = "取价接口")
@RestController
@RequestMapping("/price/query")
public class PriceQueryController extends BaseController {

    @Autowired
    private PriceQueryService priceQueryService;

    /**
     * 按客户正式报价取价（配送点覆盖价/报价模板已下线，见蓝图 W0-1）
     */
    @PreAuthorize("@ss.hasPermi('order:sale:add')")
    @GetMapping
    public AjaxResult query(@RequestParam("customerId") Long customerId,
                            @RequestParam(value = "deliveryPointId", required = false) Long deliveryPointId,
                            @RequestParam("skuId") Long skuId,
                            @RequestParam("deliveryDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate deliveryDate) {
        return success(priceQueryService.queryPrice(customerId, deliveryPointId, skuId, deliveryDate));
    }
}

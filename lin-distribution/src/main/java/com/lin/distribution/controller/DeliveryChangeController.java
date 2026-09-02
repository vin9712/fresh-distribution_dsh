package com.lin.distribution.controller;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.distribution.service.DeliveryChangeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 配送后订单变更接口（D-055：加单/换货/退货，原订单数据不变，标记附加在订单明细）
 *
 * @author dsh
 */
@Tag(name = "配送后订单变更")
@RestController
@RequestMapping("/order/delivery-change")
public class DeliveryChangeController extends BaseController {

    private final DeliveryChangeService deliveryChangeService;

    public DeliveryChangeController(DeliveryChangeService deliveryChangeService) {
        this.deliveryChangeService = deliveryChangeService;
    }

    @Operation(summary = "配送后加单（补充单据：应收+实收，change_type=1）")
    @PreAuthorize("@ss.hasPermi('order:sale:edit')")
    @PostMapping("/{orderId}/supplement")
    public AjaxResult supplement(@PathVariable("orderId") Long orderId, @RequestBody Map<String, Object> body) {
        Long skuId = toLong(body.get("skuId"));
        BigDecimal num = toDecimal(body.get("num"));
        BigDecimal price = toDecimal(body.get("price"));
        BigDecimal actualNum = toDecimal(body.get("actualNum"));
        return success(deliveryChangeService.addSupplement(orderId, skuId,
                str(body, "productName"), str(body, "spec"), str(body, "unit"),
                num, price, actualNum, str(body, "remark")));
    }

    @Operation(summary = "配送后换货（原行标退货+新增换货行，同组，change_type=2/3）")
    @PreAuthorize("@ss.hasPermi('order:sale:edit')")
    @PostMapping("/{orderId}/exchange")
    public AjaxResult exchange(@PathVariable("orderId") Long orderId, @RequestBody Map<String, Object> body) {
        Long targetDetailId = toLong(body.get("targetDetailId"));
        Long skuId = toLong(body.get("skuId"));
        BigDecimal num = toDecimal(body.get("num"));
        BigDecimal actualNum = toDecimal(body.get("actualNum"));
        return success(deliveryChangeService.exchange(orderId, targetDetailId, skuId,
                str(body, "productName"), str(body, "spec"), str(body, "unit"),
                num, actualNum, str(body, "remark")));
    }

    @Operation(summary = "配送后退货（原行标退货，change_type=3，应送实收归0）")
    @PreAuthorize("@ss.hasPermi('order:sale:edit')")
    @PostMapping("/{orderId}/return")
    public AjaxResult returnLine(@PathVariable("orderId") Long orderId, @RequestBody Map<String, Object> body) {
        Long targetDetailId = toLong(body.get("targetDetailId"));
        return success(deliveryChangeService.returnLine(orderId, targetDetailId, str(body, "remark")));
    }

    private Long toLong(Object v) {
        if (v == null || String.valueOf(v).isBlank() || "null".equalsIgnoreCase(String.valueOf(v))) {
            return null;
        }
        return Long.valueOf(String.valueOf(v));
    }

    private BigDecimal toDecimal(Object v) {
        if (v == null || String.valueOf(v).isBlank() || "null".equalsIgnoreCase(String.valueOf(v))) {
            return null;
        }
        return new BigDecimal(String.valueOf(v));
    }

    private String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank() || "null".equalsIgnoreCase(String.valueOf(v))) {
            return null;
        }
        return String.valueOf(v);
    }
}

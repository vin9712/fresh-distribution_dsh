package com.lin.distribution.controller;

import com.lin.common.core.controller.BaseController;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 打印数据接口（供 JimuReport API 数据集调用）
 * /print/deliveryData 由 SecurityConfig 放行（JimuReport 服务端调用不带 JWT），
 * 返回扁平化送货单打印数据（表头字段随行冗余，供简单模板直接渲染）。
 *
 * @author dsh
 */
@Tag(name = "打印数据接口")
@RestController
@RequestMapping("/print")
public class PrintController extends BaseController {
    @Autowired
    private DeliveryOrderMapper deliveryOrderMapper;
    @Autowired
    private DeliveryOrderDetailMapper deliveryOrderDetailMapper;

    /**
     * 送货单表头打印数据（JimuReport 单值数据集 hd）
     *
     * @param deliveryOrderId 送货单ID
     * @return {"head":{...}}
     */
    @Operation(summary = "送货单表头打印数据")
    @GetMapping("/deliveryHead")
    public Map<String, Object> deliveryHead(@RequestParam("deliveryOrderId") Long deliveryOrderId) {
        Map<String, Object> resp = new LinkedHashMap<>();
        Map<String, Object> head = new LinkedHashMap<>();
        resp.put("head", head);
        if (deliveryOrderId == null) {
            return resp;
        }
        DeliveryOrder order = deliveryOrderMapper.selectDeliveryOrderById(deliveryOrderId);
        if (order == null) {
            return resp;
        }
        List<DeliveryOrderDetail> details = deliveryOrderDetailMapper.selectListByDeliveryId(deliveryOrderId);
        BigDecimal total = BigDecimal.ZERO;
        for (DeliveryOrderDetail detail : details) {
            total = total.add(detail.getAmount() == null ? BigDecimal.ZERO : detail.getAmount());
        }
        head.put("code", order.getCode());
        head.put("customerName", order.getCustomerName());
        head.put("deliveryPointName", order.getCustomerDeptName());
        head.put("deliveryDate", order.getDeliveryDate() == null ? "" : order.getDeliveryDate().toString());
        head.put("totalAmount", total);
        return resp;
    }

    /**
     * 送货单明细打印数据（JimuReport 列表数据集 dd）
     *
     * @param deliveryOrderId 送货单ID
     * @return {"rows":[...]}
     */
    @Operation(summary = "送货单明细打印数据")
    @GetMapping("/deliveryData")
    public Map<String, Object> deliveryData(@RequestParam("deliveryOrderId") Long deliveryOrderId) {
        Map<String, Object> resp = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        resp.put("rows", rows);
        if (deliveryOrderId == null) {
            return resp;
        }
        DeliveryOrder order = deliveryOrderMapper.selectDeliveryOrderById(deliveryOrderId);
        if (order == null) {
            return resp;
        }
        List<DeliveryOrderDetail> details = deliveryOrderDetailMapper.selectListByDeliveryId(deliveryOrderId);
        for (DeliveryOrderDetail detail : details) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productName", detail.getProductName());
            row.put("productSpec", detail.getProductSpec());
            row.put("productUnit", detail.getProductUnit());
            row.put("num", detail.getNum());
            row.put("price", detail.getPrice());
            row.put("amount", detail.getAmount());
            rows.add(row);
        }
        return resp;
    }
}

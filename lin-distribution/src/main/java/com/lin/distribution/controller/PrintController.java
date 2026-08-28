package com.lin.distribution.controller;

import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.CustomerSkuMapping;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.mapper.CustomerSkuMappingMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.service.PrintTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 打印数据接口（供 JimuReport API 数据集调用）
 * 
 * <p>W0-4.1 票据鉴权：/print/deliveryHead、/print/deliveryData 仍由 SecurityConfig 放行
 * （JimuReport 服务端回调不带 JWT），但改为强校验短时一次性打印票据（ticket 参数，
 * 由报表视图 URL 经数据集 URL 占位符 {@code ${ticket}} 透传），且票据必须与请求
 * deliveryOrderId 绑定一致，不再是无凭据裸奔。
 * 签发接口 {@code POST /print/ticket} 走正常 JWT 过滤器 + RBAC 权限。
 * 打印品名客户映射叫法优先（DESIGN 不变量 8：无映射用我方品名快照）。
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
    @Autowired
    private CustomerSkuMappingMapper customerSkuMappingMapper;
    @Autowired
    private PrintTicketService printTicketService;

    /**
     * 签发短时一次性打印票据（W0-4.1：替代 URL 携带长期 JWT）
     * 打印送货单用 order:delivery:print；打开报表设计器/预览用 print:template:list。
     *
     * @param body {deliveryOrderId?: Long, templateId?: Long}
     * @return {ticket: "ptk_..."}，TTL 300 秒、一次性兑换
     */
    @Operation(summary = "签发短时一次性打印票据")
    @PreAuthorize("@ss.hasAnyPermi('order:delivery:print,print:template:list')")
    @PostMapping("/ticket")
    public AjaxResult issueTicket(@RequestBody(required = false) Map<String, Object> body) {
        Long deliveryOrderId = body == null ? null : toLong(body.get("deliveryOrderId"));
        Long templateId = body == null ? null : toLong(body.get("templateId"));
        String ticket = printTicketService.issue(deliveryOrderId, templateId);
        AjaxResult result = AjaxResult.success();
        result.put("ticket", ticket);
        return result;
    }

    private Long toLong(Object value) {
        if (value == null || StringUtils.isBlank(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new ServiceException("非法的打印票据参数: " + value);
        }
    }

    /**
     * 送货单表头打印数据（JimuReport 单值数据集 hd）
     * W0-4.1：必须携带与 deliveryOrderId 绑定一致的短时一次性票据
     *
     * @param deliveryOrderId 送货单ID
     * @param ticket          打印票据（报表视图 URL 透传）
     * @return {"head":{...}}
     */
    @Operation(summary = "送货单表头打印数据")
    @GetMapping("/deliveryHead")
    public Map<String, Object> deliveryHead(@RequestParam("deliveryOrderId") Long deliveryOrderId,
                                            @RequestParam(value = "ticket", required = false) String ticket) {
        checkTicket(deliveryOrderId, ticket);
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
     * W0-4.1：必须携带与 deliveryOrderId 绑定一致的短时一次性票据
     *
     * @param deliveryOrderId 送货单ID
     * @param ticket          打印票据（报表视图 URL 透传）
     * @return {"rows":[...]}
     */
    @Operation(summary = "送货单明细打印数据")
    @GetMapping("/deliveryData")
    public Map<String, Object> deliveryData(@RequestParam("deliveryOrderId") Long deliveryOrderId,
                                            @RequestParam(value = "ticket", required = false) String ticket) {
        checkTicket(deliveryOrderId, ticket);
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
        // 客户 SKU 映射：品名客户叫法优先（无映射用我方品名快照）
        CustomerSkuMapping query = new CustomerSkuMapping();
        query.setCustomerId(order.getCustomerId());
        Map<Long, String> aliasBySku = customerSkuMappingMapper.selectCustomerSkuMappingList(query).stream()
                .filter(m -> m.getSkuId() != null && StringUtils.isNotBlank(m.getCustomerAlias()))
                .collect(Collectors.toMap(CustomerSkuMapping::getSkuId, CustomerSkuMapping::getCustomerAlias, (a, b) -> a));
        for (DeliveryOrderDetail detail : details) {
            Map<String, Object> row = new LinkedHashMap<>();
            String productName = detail.getSkuId() != null ? aliasBySku.get(detail.getSkuId()) : null;
            row.put("productName", StringUtils.isBlank(productName) ? detail.getProductName() : productName);
            row.put("productSpec", detail.getProductSpec());
            row.put("productUnit", detail.getProductUnit());
            row.put("num", detail.getNum());
            row.put("price", detail.getPrice());
            row.put("amount", detail.getAmount());
            rows.add(row);
        }
        return resp;
    }

    /**
     * W0-4.1：数据接口票据强校验（无效/过期/与送货单绑定不一致一律拒绝）
     */
    private void checkTicket(Long deliveryOrderId, String ticket) {
        if (!printTicketService.validateDataAccess(ticket, deliveryOrderId)) {
            throw new ServiceException("打印票据无效、过期或与单据不匹配，请回到系统重新打印");
        }
    }
}

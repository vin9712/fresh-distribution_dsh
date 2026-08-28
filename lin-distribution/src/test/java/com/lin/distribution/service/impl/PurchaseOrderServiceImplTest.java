package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.PurchaseOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.PurchaseItem;
import com.lin.distribution.domain.PurchaseModifyLog;
import com.lin.distribution.domain.PurchaseOrder;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.dto.PurchaseByOrdersDTO;
import com.lin.distribution.mapper.MonthSettlementMapper;
import com.lin.distribution.mapper.PurchaseItemMapper;
import com.lin.distribution.mapper.PurchaseModifyLogMapper;
import com.lin.distribution.mapper.PurchaseOrderMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购单按勾选订单生成测试（Phase 2：销售订单列表页抽屉 generateByOrderIds）
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceImplTest {

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private PurchaseItemMapper purchaseItemMapper;
    @Mock
    private PurchaseModifyLogMapper purchaseModifyLogMapper;
    @Mock
    private MonthSettlementMapper monthSettlementMapper;
    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private PurchaseOrderServiceImpl purchaseOrderService;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 22);
    private static final Long ORDER_1 = 100L;
    private static final Long ORDER_2 = 101L;
    private static final Long SKU_1 = 11L;
    private static final Long SKU_2 = 22L;

    @BeforeEach
    void setUp() {
        // 默认：无已存在的自动采购单（幂等检查通过）
        lenient().when(purchaseOrderMapper.selectPurchaseOrderList(any(PurchaseOrder.class)))
                .thenReturn(Collections.emptyList());
        lenient().when(bizCodeService.nextDailyCode("purchase", "PC", 3))
                .thenReturn("PC20260822001");
    }

    private SaleOrder order(Long id, String code, Integer status) {
        SaleOrder order = new SaleOrder();
        order.setId(id);
        order.setCode(code);
        order.setStatus(status);
        order.setDeliveryDate(DATE);
        order.setAmount(new BigDecimal("10.00"));
        return order;
    }

    private PurchaseItem item(Long skuId, String name, String spec, String unit, String num, String price) {
        PurchaseItem item = new PurchaseItem();
        item.setSkuId(skuId);
        item.setProductName(name);
        item.setProductSpec(spec);
        item.setProductUnit(unit);
        item.setQuantity(new BigDecimal(num));
        item.setUnitPrice(new BigDecimal(price));
        return item;
    }

    @Test
    void 订单集合为空应报错() {
        assertThrows(ServiceException.class,
                () -> purchaseOrderService.generateByOrderIds(PurchaseByOrdersDTO.builder().orderIds(Collections.emptyList()).build()));
    }

    @Test
    void 部分订单不存在应报错() {
        when(saleOrderMapper.selectSaleOrderByIdIn(Arrays.asList(ORDER_1, ORDER_2)))
                .thenReturn(Collections.singletonList(order(ORDER_1, "XD1", SaleOrderStatus.CONFIRMED.getCode())));
        assertThrows(ServiceException.class, () -> purchaseOrderService.generateByOrderIds(
                PurchaseByOrdersDTO.builder().orderIds(Arrays.asList(ORDER_1, ORDER_2)).build()));
    }

    @Test
    void 含非审核状态订单应报错() {
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(ORDER_1)))
                .thenReturn(Collections.singletonList(order(ORDER_1, "XD1", SaleOrderStatus.DRAFT.getCode())));
        assertThrows(ServiceException.class, () -> purchaseOrderService.generateByOrderIds(
                PurchaseByOrdersDTO.builder().orderIds(Collections.singletonList(ORDER_1)).build()));
    }

    @Test
    void 配送日期不一致应报错() {
        SaleOrder orderA = order(ORDER_1, "XD1", SaleOrderStatus.CONFIRMED.getCode());
        SaleOrder orderB = order(ORDER_2, "XD2", SaleOrderStatus.CONFIRMED.getCode());
        orderB.setDeliveryDate(DATE.plusDays(1));
        when(saleOrderMapper.selectSaleOrderByIdIn(Arrays.asList(ORDER_1, ORDER_2)))
                .thenReturn(Arrays.asList(orderA, orderB));
        assertThrows(ServiceException.class, () -> purchaseOrderService.generateByOrderIds(
                PurchaseByOrdersDTO.builder().orderIds(Arrays.asList(ORDER_1, ORDER_2)).build()));
    }

    @Test
    void 订单已在自动采购单中应拒绝重复生成() {
        PurchaseOrder exist = new PurchaseOrder();
        exist.setCode("PC20260822001");
        exist.setSourceOrderIds("[100]");
        when(purchaseOrderMapper.selectPurchaseOrderList(any(PurchaseOrder.class)))
                .thenReturn(Collections.singletonList(exist));
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(ORDER_1)))
                .thenReturn(Collections.singletonList(order(ORDER_1, "XD1", SaleOrderStatus.CONFIRMED.getCode())));
        assertThrows(ServiceException.class, () -> purchaseOrderService.generateByOrderIds(
                PurchaseByOrdersDTO.builder().orderIds(Collections.singletonList(ORDER_1)).build()));
    }

    @Test
    void 无汇总明细应报错() {
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(ORDER_1)))
                .thenReturn(Collections.singletonList(order(ORDER_1, "XD1", SaleOrderStatus.CONFIRMED.getCode())));
        when(purchaseItemMapper.selectSummaryByOrderIds(Collections.singletonList(ORDER_1)))
                .thenReturn(Collections.emptyList());
        assertThrows(ServiceException.class, () -> purchaseOrderService.generateByOrderIds(
                PurchaseByOrdersDTO.builder().orderIds(Collections.singletonList(ORDER_1)).build()));
    }

    @Test
    void 正常生成采购单含供应商采购员与明细汇总() {
        when(saleOrderMapper.selectSaleOrderByIdIn(Arrays.asList(ORDER_1, ORDER_2)))
                .thenReturn(Arrays.asList(
                        order(ORDER_1, "XD1", SaleOrderStatus.CONFIRMED.getCode()),
                        order(ORDER_2, "XD2", SaleOrderStatus.CONFIRMED.getCode())));
        when(purchaseItemMapper.selectSummaryByOrderIds(Arrays.asList(ORDER_1, ORDER_2)))
                .thenReturn(Arrays.asList(
                        item(SKU_1, "白菜", "", "斤", "5", "1.20"),
                        item(SKU_2, "土豆", "大", "斤", "3", "1.60")));
        when(purchaseOrderMapper.insertPurchaseOrder(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            ((PurchaseOrder) invocation.getArgument(0)).setId(99L);
            return 1;
        });

        PurchaseOrder result = purchaseOrderService.generateByOrderIds(PurchaseByOrdersDTO.builder()
                .orderIds(Arrays.asList(ORDER_1, ORDER_2))
                .supplierName("城北农批")
                .purchaser("张三")
                .build());

        assertNotNull(result.getId());
        assertEquals("PC20260822001", result.getCode());
        assertEquals(DATE, result.getOrderDate());
        assertEquals(PurchaseOrderStatus.DRAFT.getCode(), result.getStatus());
        assertEquals("[100,101]", result.getSourceOrderIds());
        assertEquals("城北农批", result.getSupplierName());
        assertEquals("张三", result.getPurchaser());
        // 总额 = 5*1.20 + 3*1.60 = 10.80
        assertEquals(0, new BigDecimal("10.80").compareTo(result.getTotalAmount()));

        // 明细批量落库：2 条，purchase_id 回填
        ArgumentCaptor<List<PurchaseItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(purchaseItemMapper).insertPurchaseItemBatch(captor.capture());
        List<PurchaseItem> saved = captor.getValue();
        assertEquals(2, saved.size());
        saved.forEach(i -> assertEquals(99L, i.getPurchaseId()));
        assertEquals(0, new BigDecimal("6.00").compareTo(saved.get(0).getSubtotal()));
        assertEquals(0, new BigDecimal("4.80").compareTo(saved.get(1).getSubtotal()));
    }

    // ==================== 已确认采购单直接调整（W0-2.5） ====================

    private PurchaseOrder confirmedPurchase(Long id, String code, String total) {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(id);
        po.setCode(code);
        po.setStatus(PurchaseOrderStatus.CONFIRMED.getCode());
        po.setTotalAmount(new BigDecimal(total));
        return po;
    }

    private PurchaseItem dbItem(Long id, Long skuId, String name, String spec, String unit, String num, String price, String subtotal) {
        PurchaseItem item = item(skuId, name, spec, unit, num, price);
        item.setId(id);
        item.setSubtotal(new BigDecimal(subtotal));
        return item;
    }

    @Test
    void 已确认采购单调整数量成本并记录前后金额审计日志() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(confirmedPurchase(99L, "PC20260822001", "10.80"));
        // 调整前明细：白菜 5@1.20=6.00、土豆 3@1.60=4.80
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(Arrays.asList(
                dbItem(1L, SKU_1, "白菜", "", "斤", "5", "1.20", "6.00"),
                dbItem(2L, SKU_2, "土豆", "大", "斤", "3", "1.60", "4.80")));
        when(purchaseOrderMapper.updatePurchaseOrder(any(PurchaseOrder.class))).thenReturn(1);
        when(purchaseItemMapper.updatePurchaseItem(any(PurchaseItem.class))).thenReturn(1);
        when(purchaseModifyLogMapper.insertPurchaseModifyLog(any(PurchaseModifyLog.class))).thenReturn(1);

        PurchaseOrder request = new PurchaseOrder();
        request.setId(99L);
        request.setRemark("单价下调");
        PurchaseItem adjust1 = new PurchaseItem();
        adjust1.setId(1L);
        adjust1.setQuantity(new BigDecimal("4"));
        adjust1.setUnitPrice(new BigDecimal("1.20"));
        PurchaseItem adjust2 = new PurchaseItem();
        adjust2.setId(2L);
        adjust2.setQuantity(new BigDecimal("5"));
        adjust2.setUnitPrice(new BigDecimal("2.00"));
        request.setItems(Arrays.asList(adjust1, adjust2));

        PurchaseOrder result = purchaseOrderService.adjustConfirmedPurchase(request);

        // 调整后总额 = 4*1.20 + 5*2.00 = 14.80
        assertEquals(0, new BigDecimal("14.80").compareTo(result.getTotalAmount()));
        // 逐行更新数量/单价/小计
        ArgumentCaptor<PurchaseItem> itemCaptor = ArgumentCaptor.forClass(PurchaseItem.class);
        verify(purchaseItemMapper, times(2)).updatePurchaseItem(itemCaptor.capture());
        assertEquals(1L, itemCaptor.getAllValues().get(0).getId());
        assertEquals(0, new BigDecimal("4").compareTo(itemCaptor.getAllValues().get(0).getQuantity()));
        assertEquals(0, new BigDecimal("4.80").compareTo(itemCaptor.getAllValues().get(0).getSubtotal()));
        assertEquals(0, new BigDecimal("10.00").compareTo(itemCaptor.getAllValues().get(1).getSubtotal()));
        // 审计日志：前后金额 + 明细快照
        ArgumentCaptor<PurchaseModifyLog> logCaptor = ArgumentCaptor.forClass(PurchaseModifyLog.class);
        verify(purchaseModifyLogMapper).insertPurchaseModifyLog(logCaptor.capture());
        PurchaseModifyLog log = logCaptor.getValue();
        assertEquals(99L, log.getPurchaseId());
        assertEquals("PC20260822001", log.getPurchaseCode());
        assertEquals(0, new BigDecimal("10.80").compareTo(log.getBeforeAmount()));
        assertEquals(0, new BigDecimal("14.80").compareTo(log.getAfterAmount()));
        assertNotNull(log.getBeforeItems());
        assertNotNull(log.getAfterItems());
        assertNotNull(log.getOperator());
        assertNotNull(log.getOperateTime());
        assertEquals("单价下调", log.getRemark());
    }

    @Test
    void 非已确认状态采购单禁止直接调整() {
        PurchaseOrder draft = confirmedPurchase(99L, "PC20260822001", "10.80");
        draft.setStatus(PurchaseOrderStatus.STOCKED.getCode());
        when(purchaseOrderMapper.selectPurchaseOrderById(99L)).thenReturn(draft);

        PurchaseOrder request = new PurchaseOrder();
        request.setId(99L);
        assertThrows(ServiceException.class, () -> purchaseOrderService.adjustConfirmedPurchase(request));
    }

    @Test
    void 调整明细含非已有行应拒绝() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(confirmedPurchase(99L, "PC20260822001", "10.80"));
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(
                Collections.singletonList(dbItem(1L, SKU_1, "白菜", "", "斤", "5", "1.20", "6.00")));

        PurchaseOrder request = new PurchaseOrder();
        request.setId(99L);
        PurchaseItem extra = new PurchaseItem();
        extra.setId(999L);
        extra.setQuantity(new BigDecimal("1"));
        extra.setUnitPrice(new BigDecimal("1.00"));
        request.setItems(Collections.singletonList(extra));

        assertThrows(ServiceException.class, () -> purchaseOrderService.adjustConfirmedPurchase(request));
    }

    @Test
    void 调整数量为负应拒绝() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(confirmedPurchase(99L, "PC20260822001", "10.80"));
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(
                Collections.singletonList(dbItem(1L, SKU_1, "白菜", "", "斤", "5", "1.20", "6.00")));

        PurchaseOrder request = new PurchaseOrder();
        request.setId(99L);
        PurchaseItem item = new PurchaseItem();
        item.setId(1L);
        item.setQuantity(new BigDecimal("-1"));
        item.setUnitPrice(new BigDecimal("1.20"));
        request.setItems(Collections.singletonList(item));

        assertThrows(ServiceException.class, () -> purchaseOrderService.adjustConfirmedPurchase(request));
    }

    @Test
    void 查询采购单调整日志返回列表() {
        when(purchaseModifyLogMapper.selectListByPurchaseId(99L))
                .thenReturn(Collections.singletonList(new PurchaseModifyLog()));
        assertEquals(1, purchaseOrderService.selectModifyLogsByPurchaseId(99L).size());
    }
}

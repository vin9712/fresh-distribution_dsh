package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.PurchaseOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.PurchaseItem;
import com.lin.distribution.domain.PurchaseOrder;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.dto.PurchaseByOrdersDTO;
import com.lin.distribution.mapper.PurchaseItemMapper;
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
}

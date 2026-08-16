package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 送货单生成/打印/送达测试（DESIGN.md §7.2，S5-1 验收：分组/合并正确）
 */
@ExtendWith(MockitoExtension.class)
class DeliveryOrderServiceImplTest {

    @Mock
    private DeliveryOrderMapper deliveryOrderMapper;
    @Mock
    private DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    @Mock
    private SaleOrderDetailMapper saleOrderDetailMapper;
    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private DeliveryOrderServiceImpl deliveryOrderService;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 17);
    private static final Long CUSTOMER_A = 100L;
    private static final Long CUSTOMER_B = 200L;
    private static final Long POINT_A1 = 101L;
    private static final Long POINT_B1 = 201L;
    private static final Long SKU_1 = 11L;
    private static final Long SKU_2 = 22L;

    @BeforeEach
    void setUp() {
        // 默认：无已存在送货单（幂等检查通过）
        lenient().when(deliveryOrderMapper.selectDeliveryOrderList(any(DeliveryOrder.class)))
                .thenReturn(Collections.emptyList());
        lenient().when(bizCodeService.nextDailyCode("deliveryOrder", "HS", 3))
                .thenReturn("HS20260817001", "HS20260817002", "HS20260817003");
    }

    private SaleOrderDetail row(Long customerId, Long pointId, Long skuId, String name,
                                String unit, String spec, String price, String num) {
        SaleOrderDetail detail = new SaleOrderDetail();
        detail.setCustomerId(customerId);
        detail.setCustomerDeptId(pointId);
        detail.setSkuId(skuId);
        detail.setProductName(name);
        detail.setProductUnit(unit);
        detail.setProductSpec(spec);
        detail.setProductPrice(new BigDecimal(price));
        detail.setNum(new BigDecimal(num));
        return detail;
    }

    @Test
    void 生成时配送日期为空应报错() {
        assertThrows(ServiceException.class, () -> deliveryOrderService.generateByDeliveryDate(null));
    }

    @Test
    void 该日期已有送货单应拒绝重复生成() {
        when(deliveryOrderMapper.selectDeliveryOrderList(any(DeliveryOrder.class)))
                .thenReturn(Collections.singletonList(new DeliveryOrder()));
        assertThrows(ServiceException.class, () -> deliveryOrderService.generateByDeliveryDate(DATE));
    }

    @Test
    void 无已确认订单应报错() {
        when(saleOrderDetailMapper.selectAggregatedByDeliveryDate(DATE)).thenReturn(Collections.emptyList());
        assertThrows(ServiceException.class, () -> deliveryOrderService.generateByDeliveryDate(DATE));
    }

    @Test
    void 生成时按客户配送点分组且明细数量金额正确() {
        // 聚合由 SQL 完成（selectAggregatedByDeliveryDate），服务按 客户+配送点 分组建单、
        // 每条聚合行落一条明细（num/price/amount 快照）
        when(saleOrderDetailMapper.selectAggregatedByDeliveryDate(DATE)).thenReturn(Arrays.asList(
                row(CUSTOMER_A, POINT_A1, SKU_1, "白菜", "斤", "", "2.00", "2"),
                row(CUSTOMER_A, POINT_A1, SKU_1, "白菜", "斤", "", "2.00", "3"),
                row(CUSTOMER_B, POINT_B1, SKU_2, "土豆", "斤", "大", "3.50", "4")
        ));
        when(deliveryOrderMapper.insertDeliveryOrder(any(DeliveryOrder.class))).thenAnswer(invocation -> {
            DeliveryOrder order = invocation.getArgument(0);
            order.setId(order.getCustomerId() == CUSTOMER_A ? 1L : 2L);
            return 1;
        });

        List<DeliveryOrder> created = deliveryOrderService.generateByDeliveryDate(DATE);

        assertEquals(2, created.size());

        DeliveryOrder first = created.get(0);
        assertEquals(CUSTOMER_A, first.getCustomerId());
        assertEquals(POINT_A1, first.getDeliveryPointId());
        assertEquals(DeliveryOrderStatus.PENDING.getCode(), first.getStatus());
        assertEquals(0, first.getPrintCount());
        assertEquals(DATE, first.getDeliveryDate());
        assertEquals("HS20260817001", first.getCode());

        // 明细 3 条：客户A 2 条 + 客户B 1 条
        ArgumentCaptor<DeliveryOrderDetail> detailCaptor = ArgumentCaptor.forClass(DeliveryOrderDetail.class);
        verify(deliveryOrderDetailMapper, org.mockito.Mockito.times(3))
                .insertDeliveryOrderDetail(detailCaptor.capture());
        DeliveryOrderDetail firstRow = detailCaptor.getAllValues().get(0);
        assertEquals(0, new BigDecimal("2.00").compareTo(firstRow.getNum()));
        assertEquals(0, new BigDecimal("2.00").compareTo(firstRow.getPrice()));
        assertEquals(0, new BigDecimal("4.00").compareTo(firstRow.getAmount()));
        assertEquals("白菜", firstRow.getProductName());
        assertEquals("", firstRow.getOrderCode());
        DeliveryOrderDetail lastRow = detailCaptor.getAllValues().get(2);
        assertEquals(CUSTOMER_B, lastRow.getCustomerId());
        assertEquals(POINT_B1, lastRow.getCustomerDeptId());
        assertEquals(0, new BigDecimal("14.00").compareTo(lastRow.getAmount()));
    }

    @Test
    void 打印时次数累加并进入已打印() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setStatus(DeliveryOrderStatus.PENDING.getCode());
        order.setPrintCount(2);
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        DeliveryOrder result = deliveryOrderService.markPrinted(9L);

        assertEquals(3, result.getPrintCount());
        assertEquals(DeliveryOrderStatus.PRINTED.getCode(), result.getStatus());
        verify(deliveryOrderMapper).updateDeliveryOrder(order);
    }

    @Test
    void 已送达不可再打印() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setStatus(DeliveryOrderStatus.DELIVERED.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        assertThrows(ServiceException.class, () -> deliveryOrderService.markPrinted(9L));
        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
    }

    @Test
    void 送达后同组订单进入已配送() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setCustomerId(CUSTOMER_A);
        order.setDeliveryPointId(POINT_A1);
        order.setDeliveryDate(DATE);
        order.setStatus(DeliveryOrderStatus.PRINTED.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        DeliveryOrder result = deliveryOrderService.markDelivered(9L);

        assertEquals(DeliveryOrderStatus.DELIVERED.getCode(), result.getStatus());
        verify(saleOrderMapper).markDeliveredByDeliveryGroup(
                eq(CUSTOMER_A), eq(POINT_A1), eq(DATE),
                eq(SaleOrderStatus.CONFIRMED.getCode()), eq(SaleOrderStatus.DELIVERED.getCode()));
    }

    @Test
    void 已送达不可重复送达() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setStatus(DeliveryOrderStatus.DELIVERED.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        assertThrows(ServiceException.class, () -> deliveryOrderService.markDelivered(9L));
        verify(saleOrderMapper, never()).markDeliveredByDeliveryGroup(any(), any(), any(), any(), any());
    }
}

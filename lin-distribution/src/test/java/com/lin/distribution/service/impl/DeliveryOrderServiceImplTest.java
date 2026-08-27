package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 送货单打印/送达测试（S14/G2：送达仅回写来源台账命中的订单）
 *
 * <p>生成路径自 S14/T3 起收敛到 {@link DeliveryGenerationServiceImpl}，
 * 生成相关测试见 {@link DeliveryGenerationServiceImplTest}。</p>
 */
@ExtendWith(MockitoExtension.class)
class DeliveryOrderServiceImplTest {

    @Mock
    private DeliveryOrderMapper deliveryOrderMapper;
    @Mock
    private DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private DeliveryOrderServiceImpl deliveryOrderService;

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
    void 送达后仅回写来源台账命中的订单() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setStatus(DeliveryOrderStatus.PRINTED.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        DeliveryOrder result = deliveryOrderService.markDelivered(9L);

        assertEquals(DeliveryOrderStatus.DELIVERED.getCode(), result.getStatus());
        // S14/G2：IN 回写，仅动 source_item 命中的订单（同客户同日未进单订单不受影响）
        verify(saleOrderMapper).updateStatusByDeliveryId(
                eq(9L),
                eq(SaleOrderStatus.CONFIRMED.getCode()),
                eq(SaleOrderStatus.DELIVERED.getCode()));
    }

    @Test
    void 已送达不可重复送达() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setStatus(DeliveryOrderStatus.DELIVERED.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        assertThrows(ServiceException.class, () -> deliveryOrderService.markDelivered(9L));
        verify(saleOrderMapper, never()).updateStatusByDeliveryId(any(), any(), any());
    }
}

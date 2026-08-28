package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.SaleOrderCreateDTO;
import com.lin.distribution.dto.SaleOrderUpdateStatusDTO;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.DeliveryOrderService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售订单编辑护栏与撤回精确化测试（S14/T2，DESIGN.md §5.5 / §七 操作可行性矩阵）
 *
 * <p>G6：DELIVERED/ACCEPTED/SETTLED 拒改；CONFIRMED 已进有效送货单拒改；
 * DRAFT / CONFIRMED 未分配可改。<br>
 * G4：撤回仅看 EXISTS source_item 有效分配，同客户同日他单有送货单不影响。</p>
 */
@ExtendWith(MockitoExtension.class)
class SaleOrderServiceImplTest {

    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private SaleOrderDetailMapper saleOrderDetailMapper;
    @Mock
    private DeliveryOrderService deliveryOrderService;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private SaleOrderServiceImpl saleOrderService;

    private static final Long ORDER_ID = 100L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 28);

    private SaleOrder order(Integer status) {
        SaleOrder order = new SaleOrder();
        order.setId(ORDER_ID);
        order.setCode("XD202608280001");
        order.setStatus(status);
        order.setDeliveryDate(DATE);
        order.setAmount(new BigDecimal("10.00"));
        return order;
    }

    private SaleOrderCreateDTO updateRequest() {
        SaleOrderDetail detail = new SaleOrderDetail();
        detail.setSkuId(11L);
        detail.setProductName("白菜");
        detail.setProductPrice(new BigDecimal("2.00"));
        detail.setNum(new BigDecimal("5"));
        return SaleOrderCreateDTO.builder()
                .orderId(ORDER_ID)
                .customerId(1000L)
                .customerDeptId(1001L)
                .orderCode("XD202608280001")
                .deliveryDate(DATE)
                .orderDetails(Collections.singletonList(detail))
                .build();
    }

    // ================= G6：整单改写护栏 =================

    @Test
    void 草稿状态可修改() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.DRAFT.getCode()));

        saleOrderService.updateSaleOrderWithDetails(updateRequest());

        verify(saleOrderMapper).updateSaleOrder(any(SaleOrder.class));
        verify(saleOrderDetailMapper).deleteSaleOrderDetailByOrderId(ORDER_ID);
    }

    @Test
    void 未分配的已确认订单可修改() {
        // §七 矩阵：CONFIRMED 未进送货单 → 编辑明细/表头 ✅
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.CONFIRMED.getCode()));
        when(saleOrderDetailMapper.existsValidAllocation(ORDER_ID)).thenReturn(false);

        saleOrderService.updateSaleOrderWithDetails(updateRequest());

        verify(saleOrderMapper).updateSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 已进有效送货单的已确认订单拒改() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.CONFIRMED.getCode()));
        when(saleOrderDetailMapper.existsValidAllocation(ORDER_ID)).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        assertTrue(ex.getMessage().contains("已生成送货单"));
        // 拒改时不得触发物理删重插（切断关联/清零镜像的风险路径）
        verify(saleOrderDetailMapper, never()).deleteSaleOrderDetailByOrderId(anyLong());
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 已配送订单拒改() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.DELIVERED.getCode()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        assertTrue(ex.getMessage().contains("新增销售订单/退货单"));
        verify(saleOrderDetailMapper, never()).existsValidAllocation(anyLong());
    }

    @Test
    void 已验收订单拒改() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.ACCEPTED.getCode()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        assertTrue(ex.getMessage().contains("新增销售订单/退货单"));
    }

    @Test
    void 已结算订单拒改() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.SETTLED.getCode()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        assertTrue(ex.getMessage().contains("已结算"));
    }

    @Test
    void 修改不存在的订单应报错() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(null);

        assertThrows(ServiceException.class, () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    // ================= G4：撤回判断精确化 =================

    @Test
    void 撤回仍被有效送货单占用的订单应拒绝() {
        SaleOrder occupied = order(SaleOrderStatus.CONFIRMED.getCode());
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(ORDER_ID)))
                .thenReturn(Collections.singletonList(occupied));
        when(saleOrderDetailMapper.existsValidAllocation(ORDER_ID)).thenReturn(true);

        SaleOrderUpdateStatusDTO request = new SaleOrderUpdateStatusDTO();
        request.setOrderIds(Collections.singletonList(ORDER_ID));
        request.setStatus(SaleOrderStatus.DRAFT.getCode());

        ServiceException ex = assertThrows(ServiceException.class, () -> saleOrderService.updateSaleOrderStatus(request));
        assertTrue(ex.getMessage().contains("已生成送货单的订单不可撤回"));
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 撤回未进单的同组订单不受他单送货单影响() {
        // G4 核心场景：同客户同日 B 单已生成送货单，A 单未进单 → A 撤回必须放行
        SaleOrder a = order(SaleOrderStatus.CONFIRMED.getCode());
        SaleOrder b = new SaleOrder();
        b.setId(101L);
        b.setCode("XD202608280002");
        b.setStatus(SaleOrderStatus.CONFIRMED.getCode());
        b.setDeliveryDate(DATE);

        List<Long> ids = Arrays.asList(ORDER_ID, 101L);
        when(saleOrderMapper.selectSaleOrderByIdIn(ids)).thenReturn(Arrays.asList(a, b));
        when(saleOrderDetailMapper.existsValidAllocation(ORDER_ID)).thenReturn(false);      // A 未进单
        when(saleOrderDetailMapper.existsValidAllocation(101L)).thenReturn(true);           // B 已进单

        SaleOrderUpdateStatusDTO request = new SaleOrderUpdateStatusDTO();
        request.setOrderIds(ids);
        request.setStatus(SaleOrderStatus.DRAFT.getCode());

        ServiceException ex = assertThrows(ServiceException.class, () -> saleOrderService.updateSaleOrderStatus(request));
        assertTrue(ex.getMessage().contains("XD202608280002"));
        // A 不应被误伤
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 撤回全部未进单订单应放行() {
        SaleOrder a = order(SaleOrderStatus.CONFIRMED.getCode());
        SaleOrder b = new SaleOrder();
        b.setId(101L);
        b.setCode("XD202608280002");
        b.setStatus(SaleOrderStatus.CONFIRMED.getCode());
        b.setDeliveryDate(DATE);

        List<Long> ids = Arrays.asList(ORDER_ID, 101L);
        when(saleOrderMapper.selectSaleOrderByIdIn(ids)).thenReturn(Arrays.asList(a, b));
        lenient().when(saleOrderDetailMapper.existsValidAllocation(ORDER_ID)).thenReturn(false);
        lenient().when(saleOrderDetailMapper.existsValidAllocation(101L)).thenReturn(false);

        SaleOrderUpdateStatusDTO request = new SaleOrderUpdateStatusDTO();
        request.setOrderIds(ids);
        request.setStatus(SaleOrderStatus.DRAFT.getCode());

        saleOrderService.updateSaleOrderStatus(request);

        ArgumentCaptor<SaleOrder> captor = ArgumentCaptor.forClass(SaleOrder.class);
        verify(saleOrderMapper, times(2)).updateSaleOrder(captor.capture());
        assertEquals(SaleOrderStatus.DRAFT.getCode(), captor.getAllValues().get(0).getStatus());
        assertEquals(SaleOrderStatus.DRAFT.getCode(), captor.getAllValues().get(1).getStatus());
    }

    // ================= 新增订单防重复：同配送点+同日期草稿检测 =================

    @Test
    void 同配送点同日期有草稿订单则返回最新一条() {
        SaleOrder latest = order(SaleOrderStatus.DRAFT.getCode());
        latest.setId(200L);
        latest.setCode("XD202608280200");
        when(saleOrderMapper.selectExistingDraftOrder(1001L, DATE))
                .thenReturn(Arrays.asList(latest, order(SaleOrderStatus.DRAFT.getCode())));

        SaleOrder result = saleOrderService.findExistingDraftOrder(1001L, DATE);

        assertEquals(200L, result.getId());
        assertEquals("XD202608280200", result.getCode());
        verify(saleOrderMapper).selectExistingDraftOrder(1001L, DATE);
    }

    @Test
    void 同配送点同日期无草稿订单则返回null() {
        when(saleOrderMapper.selectExistingDraftOrder(1001L, DATE))
                .thenReturn(Collections.emptyList());

        SaleOrder result = saleOrderService.findExistingDraftOrder(1001L, DATE);

        assertEquals(null, result);
    }

    @Test
    void 防重复检测参数缺一不查返回null() {
        SaleOrder result1 = saleOrderService.findExistingDraftOrder(null, DATE);
        SaleOrder result2 = saleOrderService.findExistingDraftOrder(1001L, null);

        assertEquals(null, result1);
        assertEquals(null, result2);
        verify(saleOrderMapper, never()).selectExistingDraftOrder(anyLong(), any());
    }
}

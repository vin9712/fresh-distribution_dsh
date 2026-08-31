package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.AcceptanceStatus;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.DeliverySourceItem;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.dto.DeliveryNoPrintDTO;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliverySourceItemMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.vo.DeliverySourceVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 送货单打印/送达/作废测试（S14/G2：送达仅回写来源台账命中的订单；T4：作废与免纸送达）
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
    private DeliverySourceItemMapper deliverySourceItemMapper;
    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private CustomerDeptMapper customerDeptMapper;
    @Mock
    private AcceptanceMapper acceptanceMapper;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private DeliveryOrderServiceImpl deliveryOrderService;

    // ==================== 打印/送达（原有） ====================

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

    // ==================== 送达·免纸原因（T4/D-018） ====================

    @Test
    void 未打印送达无免纸原因应拒绝() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setStatus(DeliveryOrderStatus.PENDING.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        // 旧入口（无免纸登记）与带空原因的新入口都拒绝
        assertThrows(ServiceException.class, () -> deliveryOrderService.markDelivered(9L));
        assertThrows(ServiceException.class, () -> deliveryOrderService.markDelivered(9L, new DeliveryNoPrintDTO()));
        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
        verify(saleOrderMapper, never()).updateStatusByDeliveryId(any(), any(), any());
    }

    @Test
    void 未打印送达其他原因必须填说明() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setStatus(DeliveryOrderStatus.PENDING.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        assertThrows(ServiceException.class, () -> deliveryOrderService.markDelivered(9L,
                DeliveryNoPrintDTO.builder().reasonCode("other").build()));
        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
    }

    @Test
    void 未打印送达带免纸原因成功且留痕备注() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setCode("HS20260829001");
        order.setStatus(DeliveryOrderStatus.PENDING.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        DeliveryOrder result = deliveryOrderService.markDelivered(9L,
                DeliveryNoPrintDTO.builder().reasonCode("customer_paperless").remark("客户无需纸质单据").build());

        assertEquals(DeliveryOrderStatus.DELIVERED.getCode(), result.getStatus());
        // 免纸登记拼接进 remark（单测环境无字典缓存，回退原编码）
        assertTrue(result.getRemark().contains("[免纸送达:customer_paperless：客户无需纸质单据]"));
        verify(deliveryOrderMapper).updateDeliveryOrder(order);
        verify(saleOrderMapper).updateStatusByDeliveryId(eq(9L), any(), any());
    }

    @Test
    void 已打印送达无需免纸原因且不拼接备注() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setStatus(DeliveryOrderStatus.PRINTED.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        DeliveryOrder result = deliveryOrderService.markDelivered(9L,
                DeliveryNoPrintDTO.builder().reasonCode("customer_paperless").build());

        assertEquals(DeliveryOrderStatus.DELIVERED.getCode(), result.getStatus());
        // 已打印单送达不走免纸登记：即使误传原因也不落备注
        assertEquals(null, result.getRemark());
        verify(saleOrderMapper).updateStatusByDeliveryId(eq(9L), any(), any());
    }

    @Test
    void 已作废不可送达() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setStatus(DeliveryOrderStatus.VOIDED.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        assertThrows(ServiceException.class, () -> deliveryOrderService.markDelivered(9L));
        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
    }

    // ==================== 作废（T4/§5.2） ====================

    @Test
    void 待打印送货单作废成功并释放来源订单() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setCode("HS20260829001");
        order.setStatus(DeliveryOrderStatus.PENDING.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);
        when(acceptanceMapper.selectAcceptanceList(any(Acceptance.class))).thenReturn(Collections.emptyList());
        when(deliverySourceItemMapper.selectListByDeliveryId(9L)).thenReturn(Collections.singletonList(
                sourceItem(9L, 1001L)));
        SaleOrder source = new SaleOrder();
        source.setId(1001L);
        source.setCode("XD1001");
        source.setStatus(SaleOrderStatus.CONFIRMED.getCode());
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(1001L)))
                .thenReturn(Collections.singletonList(source));

        deliveryOrderService.voidDeliveryOrder(9L, "order_error", "录错配送点");

        // 状态置 VOIDED + 原因/人/时间（单测无字典缓存回退编码；无登录上下文回落 system）
        ArgumentCaptor<DeliveryOrder> captor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderMapper).updateDeliveryOrder(captor.capture());
        DeliveryOrder voided = captor.getValue();
        assertEquals(DeliveryOrderStatus.VOIDED.getCode(), voided.getStatus());
        assertEquals("order_error：录错配送点", voided.getVoidReason());
        assertEquals("system", voided.getVoidBy());
        assertNotNull(voided.getVoidTime());
        // 来源分配软删释放（G4 解除锁定，订单可重新生成）
        verify(deliverySourceItemMapper).deleteByDeliveryId(9L);
    }

    @Test
    void 已打印送货单可作废重开() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setCode("HS20260829001");
        order.setStatus(DeliveryOrderStatus.PRINTED.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);
        when(acceptanceMapper.selectAcceptanceList(any(Acceptance.class))).thenReturn(Collections.emptyList());
        when(deliverySourceItemMapper.selectListByDeliveryId(9L)).thenReturn(Collections.emptyList());

        deliveryOrderService.voidDeliveryOrder(9L, "duplicate", null);

        ArgumentCaptor<DeliveryOrder> captor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderMapper).updateDeliveryOrder(captor.capture());
        assertEquals(DeliveryOrderStatus.VOIDED.getCode(), captor.getValue().getStatus());
        // 无补充说明时原因仅存标签
        assertEquals("duplicate", captor.getValue().getVoidReason());
        verify(deliverySourceItemMapper).deleteByDeliveryId(9L);
    }

    @Test
    void 已作废不可重复作废() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setStatus(DeliveryOrderStatus.VOIDED.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        assertThrows(ServiceException.class, () -> deliveryOrderService.voidDeliveryOrder(9L, "order_error", null));
        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
        verify(deliverySourceItemMapper, never()).deleteByDeliveryId(any());
    }

    @Test
    void 已送达送货单不可作废() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setStatus(DeliveryOrderStatus.DELIVERED.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);

        assertThrows(ServiceException.class, () -> deliveryOrderService.voidDeliveryOrder(9L, "order_error", null));
        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
        verify(deliverySourceItemMapper, never()).deleteByDeliveryId(any());
    }

    @Test
    void 作废参数校验_原因必填_其他必须填说明() {
        assertThrows(ServiceException.class, () -> deliveryOrderService.voidDeliveryOrder(9L, null, null));
        assertThrows(ServiceException.class, () -> deliveryOrderService.voidDeliveryOrder(9L, "  ", null));
        assertThrows(ServiceException.class, () -> deliveryOrderService.voidDeliveryOrder(9L, "other", " "));
        // 参数校验前置，不触发任何查询
        verify(deliveryOrderMapper, never()).selectDeliveryOrderById(any());
    }

    @Test
    void 已提交验收的送货单不可作废() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setStatus(DeliveryOrderStatus.PENDING.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);
        Acceptance submitted = new Acceptance();
        submitted.setId(501L);
        submitted.setDeliveryOrderId(9L);
        submitted.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        when(acceptanceMapper.selectAcceptanceList(any(Acceptance.class)))
                .thenReturn(Collections.singletonList(submitted));

        assertThrows(ServiceException.class, () -> deliveryOrderService.voidDeliveryOrder(9L, "order_error", null));
        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
        verify(deliverySourceItemMapper, never()).deleteByDeliveryId(any());
    }

    @Test
    void 来源订单已结算的送货单不可作废() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(9L);
        order.setCode("HS20260829001");
        order.setStatus(DeliveryOrderStatus.PENDING.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(9L)).thenReturn(order);
        when(acceptanceMapper.selectAcceptanceList(any(Acceptance.class))).thenReturn(Collections.emptyList());
        when(deliverySourceItemMapper.selectListByDeliveryId(9L)).thenReturn(List.of(
                sourceItem(9L, 1001L), sourceItem(9L, 1002L)));
        SaleOrder settled = new SaleOrder();
        settled.setId(1001L);
        settled.setCode("XD1001");
        settled.setStatus(SaleOrderStatus.SETTLED.getCode());
        when(saleOrderMapper.selectSaleOrderByIdIn(anyList())).thenReturn(Collections.singletonList(settled));

        ServiceException e = assertThrows(ServiceException.class,
                () -> deliveryOrderService.voidDeliveryOrder(9L, "order_error", null));
        // 拒绝信息带出已结算订单号，便于定位
        assertTrue(e.getMessage().contains("XD1001"));
        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
        verify(deliverySourceItemMapper, never()).deleteByDeliveryId(any());
    }

    @Test
    void 送货单不存在不可作废() {
        when(deliveryOrderMapper.selectDeliveryOrderById(404L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> deliveryOrderService.voidDeliveryOrder(404L, "order_error", null));
        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
    }

    // ==================== 造数工具 ====================

    private DeliverySourceItem sourceItem(Long deliveryId, Long saleOrderId) {
        DeliverySourceItem item = new DeliverySourceItem();
        item.setDeliveryId(deliveryId);
        item.setSaleOrderId(saleOrderId);
        return item;
    }

    // ==================== 来源视图（selectDeliverySources，S14 §6.1） ====================

    @Test
    void 来源视图展开来源订单并回填订单号与配送点名() {
        DeliveryOrderDetail detail = new DeliveryOrderDetail();
        detail.setId(700L);
        detail.setProductName("白菜");
        detail.setNum(new BigDecimal("10"));
        detail.setPrice(new BigDecimal("2.50"));
        detail.setAmount(new BigDecimal("25.00"));
        when(deliveryOrderDetailMapper.selectListByDeliveryId(500L)).thenReturn(List.of(detail));

        DeliverySourceItem siA = sourceItem(500L, 1000L);
        siA.setId(1L);
        siA.setDeliveryDetailId(700L);
        siA.setCustomerDeptId(201L);
        siA.setAllocatedQuantity(new BigDecimal("4"));
        siA.setUnitPrice(new BigDecimal("2.50"));
        DeliverySourceItem siB = sourceItem(500L, 1001L);
        siB.setId(2L);
        siB.setDeliveryDetailId(700L);
        siB.setCustomerDeptId(202L);
        siB.setAllocatedQuantity(new BigDecimal("6"));
        siB.setUnitPrice(new BigDecimal("2.50"));
        when(deliverySourceItemMapper.selectListByDeliveryId(500L)).thenReturn(List.of(siA, siB));

        SaleOrder orderA = new SaleOrder();
        orderA.setId(1000L);
        orderA.setCode("XS001");
        SaleOrder orderB = new SaleOrder();
        orderB.setId(1001L);
        orderB.setCode("XS002");
        when(saleOrderMapper.selectSaleOrderByIdIn(anyList())).thenReturn(List.of(orderA, orderB));
        CustomerDept deptA = new CustomerDept();
        deptA.setId(201L);
        deptA.setName("点一");
        CustomerDept deptB = new CustomerDept();
        deptB.setId(202L);
        deptB.setName("点二");
        when(customerDeptMapper.selectCustomerDeptById(201L)).thenReturn(deptA);
        when(customerDeptMapper.selectCustomerDeptById(202L)).thenReturn(deptB);

        List<DeliverySourceVO> result = deliveryOrderService.selectDeliverySources(500L);

        assertEquals(1, result.size());
        DeliverySourceVO vo = result.get(0);
        assertEquals(new BigDecimal("10"), vo.getNum());
        assertEquals(2, vo.getSources().size());
        assertEquals("XS001", vo.getSources().get(0).getOrderCode());
        assertEquals("点二", vo.getSources().get(1).getCustomerDeptName());
        assertEquals(new BigDecimal("6"), vo.getSources().get(1).getAllocatedQuantity());
    }

    @Test
    void 来源视图历史单无台账时sources为空但不丢聚合行() {
        DeliveryOrderDetail detail = new DeliveryOrderDetail();
        detail.setId(700L);
        detail.setProductName("土豆");
        detail.setNum(new BigDecimal("8"));
        when(deliveryOrderDetailMapper.selectListByDeliveryId(500L)).thenReturn(List.of(detail));
        when(deliverySourceItemMapper.selectListByDeliveryId(500L)).thenReturn(Collections.emptyList());

        List<DeliverySourceVO> result = deliveryOrderService.selectDeliverySources(500L);

        assertEquals(1, result.size());
        assertEquals("土豆", result.get(0).getProductName());
        assertTrue(result.get(0).getSources().isEmpty());
        verify(saleOrderMapper, never()).selectSaleOrderByIdIn(anyList());
    }

    // ==================== 列表待验收提醒标色（W0-3.2） ====================

    @Test
    void 列表已送达过期未验收行标红() {
        DeliveryOrder expired = new DeliveryOrder();
        expired.setId(1L);
        expired.setStatus(DeliveryOrderStatus.DELIVERED.getCode());
        expired.setDeliveryDate(java.time.LocalDate.now().minusDays(1));
        when(deliveryOrderMapper.selectDeliveryOrderList(any(DeliveryOrder.class)))
                .thenReturn(new ArrayList<>(List.of(expired)));
        when(acceptanceMapper.selectSubmittedDeliveryIds(anyList())).thenReturn(Collections.emptyList());

        List<DeliveryOrder> result = deliveryOrderService.selectDeliveryOrderList(new DeliveryOrder());

        assertEquals(2, result.get(0).getReminderLevel());
        assertNotNull(result.get(0).getReminderReason());
    }

    @Test
    void 列表已提交验收单的送达行不标色() {
        DeliveryOrder accepted = new DeliveryOrder();
        accepted.setId(2L);
        accepted.setStatus(DeliveryOrderStatus.DELIVERED.getCode());
        accepted.setDeliveryDate(java.time.LocalDate.now().minusDays(1));
        when(deliveryOrderMapper.selectDeliveryOrderList(any(DeliveryOrder.class)))
                .thenReturn(new ArrayList<>(List.of(accepted)));
        when(acceptanceMapper.selectSubmittedDeliveryIds(anyList())).thenReturn(List.of(2L));

        List<DeliveryOrder> result = deliveryOrderService.selectDeliveryOrderList(new DeliveryOrder());

        assertNull(result.get(0).getReminderLevel());
    }

    @Test
    void 列表非送达状态与未来配送日不标色() {
        DeliveryOrder printed = new DeliveryOrder();
        printed.setId(3L);
        printed.setStatus(DeliveryOrderStatus.PRINTED.getCode());
        printed.setDeliveryDate(java.time.LocalDate.now().minusDays(1));
        DeliveryOrder future = new DeliveryOrder();
        future.setId(4L);
        future.setStatus(DeliveryOrderStatus.DELIVERED.getCode());
        future.setDeliveryDate(java.time.LocalDate.now().plusDays(1));
        when(deliveryOrderMapper.selectDeliveryOrderList(any(DeliveryOrder.class)))
                .thenReturn(new ArrayList<>(List.of(printed, future)));
        when(acceptanceMapper.selectSubmittedDeliveryIds(anyList())).thenReturn(Collections.emptyList());

        List<DeliveryOrder> result = deliveryOrderService.selectDeliveryOrderList(new DeliveryOrder());

        assertNull(result.get(0).getReminderLevel());
        assertNull(result.get(1).getReminderLevel());
    }

    @Test
    void 列表无送达行时不查验收单() {
        DeliveryOrder pending = new DeliveryOrder();
        pending.setId(5L);
        pending.setStatus(DeliveryOrderStatus.PENDING.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderList(any(DeliveryOrder.class)))
                .thenReturn(new ArrayList<>(List.of(pending)));

        List<DeliveryOrder> result = deliveryOrderService.selectDeliveryOrderList(new DeliveryOrder());

        assertNull(result.get(0).getReminderLevel());
        verify(acceptanceMapper, never()).selectSubmittedDeliveryIds(anyList());
    }
}

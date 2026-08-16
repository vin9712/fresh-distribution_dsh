package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.AcceptanceStatus;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.AcceptanceItem;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.dto.AcceptanceUpdateDTO;
import com.lin.distribution.mapper.AcceptanceItemMapper;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验收单服务测试（DESIGN.md 验收标准 7-9：一单一验、后端重算、损耗可负+原因）
 */
@ExtendWith(MockitoExtension.class)
class AcceptanceServiceImplTest {

    @Mock
    private AcceptanceMapper acceptanceMapper;
    @Mock
    private AcceptanceItemMapper acceptanceItemMapper;
    @Mock
    private DeliveryOrderMapper deliveryOrderMapper;
    @Mock
    private DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private AcceptanceServiceImpl acceptanceService;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 17);
    private static final Long CUSTOMER = 100L;
    private static final Long POINT = 101L;
    private static final Long DELIVERY_ID = 500L;

    private DeliveryOrder deliveredOrder() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(DELIVERY_ID);
        order.setCustomerId(CUSTOMER);
        order.setDeliveryPointId(POINT);
        order.setDeliveryDate(DATE);
        order.setStatus(DeliveryOrderStatus.DELIVERED.getCode());
        return order;
    }

    private DeliveryOrderDetail detail(Long id, String price, String num) {
        DeliveryOrderDetail detail = new DeliveryOrderDetail();
        detail.setId(id);
        detail.setSkuId(11L);
        detail.setProductName("白菜");
        detail.setProductUnit("斤");
        detail.setProductSpec("");
        detail.setPrice(new BigDecimal(price));
        detail.setNum(new BigDecimal(num));
        return detail;
    }

    private AcceptanceItem item(Long id, Long acceptanceId, String delivered, String price) {
        AcceptanceItem item = new AcceptanceItem();
        item.setId(id);
        item.setAcceptanceId(acceptanceId);
        item.setProductName("白菜");
        item.setDeliveredQuantity(new BigDecimal(delivered));
        item.setUnitPrice(new BigDecimal(price));
        return item;
    }

    private Acceptance draftAcceptance() {
        Acceptance acceptance = new Acceptance();
        acceptance.setId(1L);
        acceptance.setCode("YS20260817001");
        acceptance.setDeliveryOrderId(DELIVERY_ID);
        acceptance.setCustomerId(CUSTOMER);
        acceptance.setDeliveryPointId(POINT);
        acceptance.setAcceptDate(DATE);
        acceptance.setStatus(AcceptanceStatus.DRAFT.getCode());
        return acceptance;
    }

    @Test
    void 未送达的送货单不可生成验收单() {
        DeliveryOrder order = deliveredOrder();
        order.setStatus(DeliveryOrderStatus.PRINTED.getCode());
        when(deliveryOrderMapper.selectDeliveryOrderById(DELIVERY_ID)).thenReturn(order);
        assertThrows(ServiceException.class, () -> acceptanceService.createByDeliveryOrder(DELIVERY_ID));
    }

    @Test
    void 同一送货单只能生成一张验收单() {
        when(deliveryOrderMapper.selectDeliveryOrderById(DELIVERY_ID)).thenReturn(deliveredOrder());
        when(acceptanceMapper.countByDeliveryOrderId(DELIVERY_ID)).thenReturn(1);
        assertThrows(ServiceException.class, () -> acceptanceService.createByDeliveryOrder(DELIVERY_ID));
    }

    @Test
    void 生成验收单时明细默认实收等于送货且金额正确() {
        when(deliveryOrderMapper.selectDeliveryOrderById(DELIVERY_ID)).thenReturn(deliveredOrder());
        when(acceptanceMapper.countByDeliveryOrderId(DELIVERY_ID)).thenReturn(0);
        when(deliveryOrderDetailMapper.selectListByDeliveryId(DELIVERY_ID))
                .thenReturn(Arrays.asList(detail(700L, "2.00", "5"), detail(701L, "3.50", "4")));
        when(bizCodeService.nextDailyCode("acceptance", "YS", 3)).thenReturn("YS20260817001");
        when(acceptanceMapper.insertAcceptance(any(Acceptance.class))).thenAnswer(inv -> {
            inv.getArgument(0, Acceptance.class).setId(1L);
            return 1;
        });

        Acceptance created = acceptanceService.createByDeliveryOrder(DELIVERY_ID);

        assertNotNull(created);
        assertEquals(AcceptanceStatus.DRAFT.getCode(), created.getStatus());
        assertEquals(0, new BigDecimal("24.00").compareTo(created.getTotalAmount()));

        ArgumentCaptor<List<AcceptanceItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(acceptanceItemMapper).insertAcceptanceItemBatch(captor.capture());
        List<AcceptanceItem> items = captor.getValue();
        assertEquals(2, items.size());
        AcceptanceItem first = items.get(0);
        assertEquals(700L, first.getDeliveryItemId());
        assertEquals(0, new BigDecimal("5.00").compareTo(first.getDeliveredQuantity()));
        assertEquals(0, new BigDecimal("5.00").compareTo(first.getActualQuantity()));
        assertEquals(0, new BigDecimal("10.00").compareTo(first.getActualAmount()));
    }

    @Test
    void 录入时实收金额按后端单价重算() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem dbItem = item(900L, 1L, "5.00", "2.00");
        when(acceptanceItemMapper.selectAcceptanceItemById(900L)).thenReturn(dbItem);

        AcceptanceUpdateDTO dto = new AcceptanceUpdateDTO();
        dto.setId(1L);
        AcceptanceUpdateDTO.Item dtoItem = new AcceptanceUpdateDTO.Item();
        dtoItem.setId(900L);
        dtoItem.setActualQuantity(new BigDecimal("6.00")); // 超送 1
        dto.setItems(Collections.singletonList(dtoItem));

        Acceptance result = acceptanceService.updateDraft(dto);

        assertEquals(0, new BigDecimal("12.00").compareTo(result.getTotalAmount()));
        ArgumentCaptor<AcceptanceItem> captor = ArgumentCaptor.forClass(AcceptanceItem.class);
        verify(acceptanceItemMapper).updateAcceptanceItem(captor.capture());
        AcceptanceItem updated = captor.getValue();
        assertEquals(0, new BigDecimal("1.00").compareTo(updated.getLossQuantity()));
        assertEquals(0, new BigDecimal("12.00").compareTo(updated.getActualAmount()));
    }

    @Test
    void 损耗为负必须填写原因() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem dbItem = item(900L, 1L, "5.00", "2.00");
        when(acceptanceItemMapper.selectAcceptanceItemById(900L)).thenReturn(dbItem);

        AcceptanceUpdateDTO dto = new AcceptanceUpdateDTO();
        dto.setId(1L);
        AcceptanceUpdateDTO.Item dtoItem = new AcceptanceUpdateDTO.Item();
        dtoItem.setId(900L);
        dtoItem.setActualQuantity(new BigDecimal("3.00")); // 损耗 -2
        dto.setItems(Collections.singletonList(dtoItem));

        ServiceException ex = assertThrows(ServiceException.class, () -> acceptanceService.updateDraft(dto));
        assertTrue(ex.getMessage().contains("损耗为负时必须填写原因"));
        verify(acceptanceItemMapper, never()).updateAcceptanceItem(any(AcceptanceItem.class));
    }

    @Test
    void 已提交不可再修改() {
        Acceptance acceptance = draftAcceptance();
        acceptance.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(acceptance);
        AcceptanceUpdateDTO dto = new AcceptanceUpdateDTO();
        dto.setId(1L);
        assertThrows(ServiceException.class, () -> acceptanceService.updateDraft(dto));
    }

    @Test
    void 提交后状态变更且同组订单进入已验收() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        when(deliveryOrderMapper.selectDeliveryOrderById(DELIVERY_ID)).thenReturn(deliveredOrder());

        Acceptance result = acceptanceService.submit(1L);

        assertEquals(AcceptanceStatus.SUBMITTED.getCode(), result.getStatus());
        verify(saleOrderMapper).updateStatusByDeliveryGroup(
                eq(CUSTOMER), eq(POINT), eq(DATE),
                eq(SaleOrderStatus.DELIVERED.getCode()), eq(SaleOrderStatus.ACCEPTED.getCode()));
    }

    @Test
    void 已提交不可重复提交() {
        Acceptance acceptance = draftAcceptance();
        acceptance.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(acceptance);
        assertThrows(ServiceException.class, () -> acceptanceService.submit(1L));
        verify(saleOrderMapper, never()).updateStatusByDeliveryGroup(any(), any(), any(), any(), any());
    }

    @Test
    void 已提交不可删除() {
        Acceptance acceptance = draftAcceptance();
        acceptance.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(acceptance);
        assertThrows(ServiceException.class, () -> acceptanceService.deleteByIds(new Long[]{1L}));
        verify(acceptanceMapper, never()).deleteAcceptanceByIds(any(Long[].class));
    }
}

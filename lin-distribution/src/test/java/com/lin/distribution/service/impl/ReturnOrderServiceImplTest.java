package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.AcceptanceStatus;
import com.lin.distribution.constant.ReturnOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.AcceptanceItem;
import com.lin.distribution.domain.DeliverySourceItem;
import com.lin.distribution.domain.ReturnItem;
import com.lin.distribution.domain.ReturnOrder;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.dto.ReturnInspectDTO;
import com.lin.distribution.dto.ReturnOrderSaveDTO;
import com.lin.distribution.mapper.AcceptanceItemMapper;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.DeliverySourceItemMapper;
import com.lin.distribution.mapper.ReturnItemMapper;
import com.lin.distribution.mapper.ReturnOrderMapper;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退货单服务测试（S14/T6，D-032/D-034/Q31）：
 * 数量上限=实收-累计已退、单价锁原验收价、金额后端重算、
 * settle_scope 提交快照、质检状态机、软删仅草稿。
 */
@ExtendWith(MockitoExtension.class)
class ReturnOrderServiceImplTest {

    @Mock
    private ReturnOrderMapper returnOrderMapper;
    @Mock
    private ReturnItemMapper returnItemMapper;
    @Mock
    private AcceptanceMapper acceptanceMapper;
    @Mock
    private AcceptanceItemMapper acceptanceItemMapper;
    @Mock
    private DeliverySourceItemMapper deliverySourceItemMapper;
    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private ReturnOrderServiceImpl returnOrderService;

    private static final Long ACCEPTANCE_ID = 1L;
    private static final Long DELIVERY_ID = 500L;
    private static final Long ACC_ITEM_ID = 10L;

    private Acceptance submittedAcceptance() {
        Acceptance acceptance = new Acceptance();
        acceptance.setId(ACCEPTANCE_ID);
        acceptance.setCode("YS20260817001");
        acceptance.setCustomerId(100L);
        acceptance.setDeliveryPointId(101L);
        acceptance.setDeliveryOrderId(DELIVERY_ID);
        acceptance.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        return acceptance;
    }

    private AcceptanceItem accItem(String actual, String price) {
        AcceptanceItem item = new AcceptanceItem();
        item.setId(ACC_ITEM_ID);
        item.setAcceptanceId(ACCEPTANCE_ID);
        item.setSkuId(11L);
        item.setProductName("白菜");
        item.setProductSpec("500g");
        item.setProductUnit("斤");
        item.setActualQuantity(new BigDecimal(actual));
        item.setUnitPrice(new BigDecimal(price));
        return item;
    }

    private ReturnItem returned(Long acceptanceItemId, String quantity) {
        ReturnItem item = new ReturnItem();
        item.setAcceptanceItemId(acceptanceItemId);
        item.setReturnQuantity(new BigDecimal(quantity));
        return item;
    }

    private ReturnOrderSaveDTO saveDto(String quantity) {
        return saveDto(quantity, null);
    }

    private ReturnOrderSaveDTO saveDto(String quantity, Long id) {
        ReturnOrderSaveDTO dto = new ReturnOrderSaveDTO();
        dto.setId(id);
        dto.setAcceptanceId(ACCEPTANCE_ID);
        ReturnOrderSaveDTO.Item dtoItem = new ReturnOrderSaveDTO.Item();
        dtoItem.setAcceptanceItemId(ACC_ITEM_ID);
        dtoItem.setReturnQuantity(quantity == null ? null : new BigDecimal(quantity));
        dto.setItems(Collections.singletonList(dtoItem));
        return dto;
    }

    private ReturnItem storedItem(String quantity) {
        ReturnItem item = new ReturnItem();
        item.setId(900L);
        item.setReturnId(1L);
        item.setAcceptanceItemId(ACC_ITEM_ID);
        item.setProductName("白菜");
        item.setReturnQuantity(new BigDecimal(quantity));
        return item;
    }

    private ReturnOrder draftReturnOrder() {
        ReturnOrder order = new ReturnOrder();
        order.setId(1L);
        order.setCode("TH20260828001");
        order.setCustomerId(100L);
        order.setCustomerDeptId(101L);
        order.setDeliveryId(DELIVERY_ID);
        order.setAcceptanceId(ACCEPTANCE_ID);
        order.setStatus(ReturnOrderStatus.DRAFT.getCode());
        return order;
    }

    private DeliverySourceItem source(Long saleOrderId) {
        DeliverySourceItem si = new DeliverySourceItem();
        si.setDeliveryId(DELIVERY_ID);
        si.setSaleOrderId(saleOrderId);
        return si;
    }

    private SaleOrder saleOrder(Integer status) {
        SaleOrder order = new SaleOrder();
        order.setId(1000L);
        order.setCode("XS20260817001");
        order.setStatus(status);
        return order;
    }

    // ==================== 新增 ====================

    @Test
    void 创建退货单成功_单价锁验收价且金额后端重算() {
        when(acceptanceMapper.selectAcceptanceById(ACCEPTANCE_ID)).thenReturn(submittedAcceptance());
        when(returnItemMapper.sumReturnedByAcceptanceItemIds(eq(List.of(ACC_ITEM_ID)), isNull()))
                .thenReturn(Collections.emptyList());
        when(acceptanceItemMapper.selectAcceptanceItemById(ACC_ITEM_ID)).thenReturn(accItem("5", "2.50"));
        when(bizCodeService.nextDailyCode("returnOrder", "TH", 3)).thenReturn("TH20260828001");
        when(returnOrderMapper.insertReturnOrder(any(ReturnOrder.class))).thenAnswer(inv -> {
            inv.getArgument(0, ReturnOrder.class).setId(1L);
            return 1;
        });

        ReturnOrder created = returnOrderService.create(saveDto("2"));

        assertEquals("TH20260828001", created.getCode());
        assertEquals(100L, created.getCustomerId());
        assertEquals(DELIVERY_ID, created.getDeliveryId());
        assertEquals(ReturnOrderStatus.DRAFT.getCode(), created.getStatus());

        ArgumentCaptor<List<ReturnItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        verify(returnItemMapper).batchInsertReturnItem(itemCaptor.capture());
        List<ReturnItem> items = itemCaptor.getValue();
        // 单价锁原验收价，金额后端重算
        assertEquals(0, new BigDecimal("2.50").compareTo(items.get(0).getUnitPrice()));
        assertEquals(0, new BigDecimal("5.00").compareTo(items.get(0).getAmount()));
        assertEquals(0, new BigDecimal("5.00").compareTo(created.getTotalAmount()));
        // 快照取自验收明细行
        assertEquals("白菜", items.get(0).getProductName());
        assertEquals("500g", items.get(0).getProductSpec());
        assertEquals("斤", items.get(0).getProductUnit());
    }

    @Test
    void 退货数量超上限拒绝() {
        when(acceptanceMapper.selectAcceptanceById(ACCEPTANCE_ID)).thenReturn(submittedAcceptance());
        // 其他退货单已退 3，实收 5 → 可退 2，本次申请 3
        when(returnItemMapper.sumReturnedByAcceptanceItemIds(eq(List.of(ACC_ITEM_ID)), isNull()))
                .thenReturn(Collections.singletonList(returned(ACC_ITEM_ID, "3")));
        when(acceptanceItemMapper.selectAcceptanceItemById(ACC_ITEM_ID)).thenReturn(accItem("5", "2.50"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> returnOrderService.create(saveDto("3")));
        assertTrue(ex.getMessage().contains("退货数量超上限"));
        assertTrue(ex.getMessage().contains("白菜"));
        verify(returnOrderMapper, never()).insertReturnOrder(any(ReturnOrder.class));
    }

    @Test
    void 退货数量必须大于0() {
        when(acceptanceMapper.selectAcceptanceById(ACCEPTANCE_ID)).thenReturn(submittedAcceptance());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> returnOrderService.create(saveDto("0")));
        assertTrue(ex.getMessage().contains("退货数量必须大于0"));
    }

    @Test
    void 退货明细不属于该验收单拒绝() {
        when(acceptanceMapper.selectAcceptanceById(ACCEPTANCE_ID)).thenReturn(submittedAcceptance());
        when(returnItemMapper.sumReturnedByAcceptanceItemIds(eq(List.of(ACC_ITEM_ID)), isNull()))
                .thenReturn(Collections.emptyList());
        AcceptanceItem other = accItem("5", "2.50");
        other.setAcceptanceId(2L);
        when(acceptanceItemMapper.selectAcceptanceItemById(ACC_ITEM_ID)).thenReturn(other);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> returnOrderService.create(saveDto("2")));
        assertTrue(ex.getMessage().contains("退货明细不属于该验收单"));
    }

    @Test
    void 仅已提交的验收单可发起退货() {
        Acceptance draft = submittedAcceptance();
        draft.setStatus(AcceptanceStatus.DRAFT.getCode());
        when(acceptanceMapper.selectAcceptanceById(ACCEPTANCE_ID)).thenReturn(draft);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> returnOrderService.create(saveDto("2")));
        assertTrue(ex.getMessage().contains("仅已提交的验收单可发起退货"));
    }

    // ==================== 改草稿 ====================

    @Test
    void 改草稿重建明细且排除自身占用() {
        when(returnOrderMapper.selectReturnOrderById(1L)).thenReturn(draftReturnOrder());
        when(acceptanceMapper.selectAcceptanceById(ACCEPTANCE_ID)).thenReturn(submittedAcceptance());
        // excludeReturnId=1：自身已有占用不计入
        when(returnItemMapper.sumReturnedByAcceptanceItemIds(List.of(ACC_ITEM_ID), 1L))
                .thenReturn(Collections.emptyList());
        when(acceptanceItemMapper.selectAcceptanceItemById(ACC_ITEM_ID)).thenReturn(accItem("5", "2.50"));

        returnOrderService.updateDraft(saveDto("3", 1L));

        verify(returnItemMapper).deleteByReturnId(1L);
        ArgumentCaptor<List<ReturnItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(returnItemMapper).batchInsertReturnItem(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(0, new BigDecimal("3").compareTo(captor.getValue().get(0).getReturnQuantity()));
        assertEquals(0, new BigDecimal("7.50").compareTo(captor.getValue().get(0).getAmount()));

        ArgumentCaptor<ReturnOrder> orderCaptor = ArgumentCaptor.forClass(ReturnOrder.class);
        verify(returnOrderMapper).updateReturnOrder(orderCaptor.capture());
        assertEquals(0, new BigDecimal("7.50").compareTo(orderCaptor.getValue().getTotalAmount()));
    }

    @Test
    void 已提交的退货单不可修改() {
        ReturnOrder submitted = draftReturnOrder();
        submitted.setStatus(ReturnOrderStatus.SUBMITTED.getCode());
        when(returnOrderMapper.selectReturnOrderById(1L)).thenReturn(submitted);

        assertThrows(ServiceException.class, () -> returnOrderService.updateDraft(saveDto("2", 1L)));
        verify(returnItemMapper, never()).deleteByReturnId(any());
    }

    // ==================== 提交（settle_scope 快照 + 上限复核） ====================

    @Test
    void 提交成功_来源订单未结算_当期冲销() {
        when(returnOrderMapper.selectReturnOrderById(1L)).thenReturn(draftReturnOrder());
        when(returnItemMapper.selectListByReturnId(1L)).thenReturn(List.of(storedItem("2")));
        when(acceptanceMapper.selectAcceptanceById(ACCEPTANCE_ID)).thenReturn(submittedAcceptance());
        when(returnItemMapper.sumReturnedByAcceptanceItemIds(List.of(ACC_ITEM_ID), 1L))
                .thenReturn(Collections.emptyList());
        when(acceptanceItemMapper.selectAcceptanceItemById(ACC_ITEM_ID)).thenReturn(accItem("5", "2.50"));
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID))
                .thenReturn(List.of(source(1000L)));
        when(saleOrderMapper.selectSaleOrderByIdIn(List.of(1000L)))
                .thenReturn(List.of(saleOrder(SaleOrderStatus.ACCEPTED.getCode())));

        ReturnOrder result = returnOrderService.submit(1L);

        assertEquals(ReturnOrderStatus.SUBMITTED.getCode(), result.getStatus());
        ArgumentCaptor<ReturnOrder> captor = ArgumentCaptor.forClass(ReturnOrder.class);
        verify(returnOrderMapper).updateReturnOrder(captor.capture());
        assertEquals(ReturnOrderStatus.SUBMITTED.getCode(), captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getSettleScope()); // 未结算 → 0 当期冲销
    }

    @Test
    void 提交成功_来源订单已结算_下期冲销() {
        when(returnOrderMapper.selectReturnOrderById(1L)).thenReturn(draftReturnOrder());
        when(returnItemMapper.selectListByReturnId(1L)).thenReturn(List.of(storedItem("2")));
        when(acceptanceMapper.selectAcceptanceById(ACCEPTANCE_ID)).thenReturn(submittedAcceptance());
        when(returnItemMapper.sumReturnedByAcceptanceItemIds(List.of(ACC_ITEM_ID), 1L))
                .thenReturn(Collections.emptyList());
        when(acceptanceItemMapper.selectAcceptanceItemById(ACC_ITEM_ID)).thenReturn(accItem("5", "2.50"));
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID))
                .thenReturn(List.of(source(1000L)));
        when(saleOrderMapper.selectSaleOrderByIdIn(List.of(1000L)))
                .thenReturn(List.of(saleOrder(SaleOrderStatus.SETTLED.getCode())));

        ReturnOrder result = returnOrderService.submit(1L);

        assertEquals(1, result.getSettleScope()); // 任一来源订单已结算 → 1 下期冲销
    }

    @Test
    void 提交时复核超退拒绝() {
        when(returnOrderMapper.selectReturnOrderById(1L)).thenReturn(draftReturnOrder());
        when(returnItemMapper.selectListByReturnId(1L)).thenReturn(List.of(storedItem("2")));
        when(acceptanceMapper.selectAcceptanceById(ACCEPTANCE_ID)).thenReturn(submittedAcceptance());
        // 其他退货单已退 5 → 可退 0
        when(returnItemMapper.sumReturnedByAcceptanceItemIds(List.of(ACC_ITEM_ID), 1L))
                .thenReturn(Collections.singletonList(returned(ACC_ITEM_ID, "5")));
        when(acceptanceItemMapper.selectAcceptanceItemById(ACC_ITEM_ID)).thenReturn(accItem("5", "2.50"));

        ServiceException ex = assertThrows(ServiceException.class, () -> returnOrderService.submit(1L));
        assertTrue(ex.getMessage().contains("退货数量超上限"));
        verify(returnOrderMapper, never()).updateReturnOrder(any(ReturnOrder.class));
    }

    @Test
    void 已提交不可重复提交() {
        ReturnOrder submitted = draftReturnOrder();
        submitted.setStatus(ReturnOrderStatus.SUBMITTED.getCode());
        when(returnOrderMapper.selectReturnOrderById(1L)).thenReturn(submitted);

        assertThrows(ServiceException.class, () -> returnOrderService.submit(1L));
        verify(returnOrderMapper, never()).updateReturnOrder(any(ReturnOrder.class));
    }

    // ==================== 质检 ====================

    @Test
    void 质检成功_逐行落质检结论() {
        ReturnOrder submitted = draftReturnOrder();
        submitted.setStatus(ReturnOrderStatus.SUBMITTED.getCode());
        when(returnOrderMapper.selectReturnOrderById(1L)).thenReturn(submitted);
        when(returnItemMapper.selectListByReturnId(1L)).thenReturn(List.of(storedItem("2")));

        ReturnInspectDTO dto = new ReturnInspectDTO();
        ReturnInspectDTO.Item dtoItem = new ReturnInspectDTO.Item();
        dtoItem.setItemId(900L);
        dtoItem.setQualityResult(1);
        dtoItem.setQualityNote("外包装完好");
        dto.setItems(List.of(dtoItem));

        ReturnOrder result = returnOrderService.inspect(1L, dto);

        assertEquals(ReturnOrderStatus.INSPECTED.getCode(), result.getStatus());
        assertEquals("system", result.getInspectedBy()); // 无登录上下文回落
        assertNotNull(result.getInspectedTime());
        ArgumentCaptor<ReturnItem> captor = ArgumentCaptor.forClass(ReturnItem.class);
        verify(returnItemMapper).updateReturnItem(captor.capture());
        assertEquals(900L, captor.getValue().getId());
        assertEquals(1, captor.getValue().getQualityResult());
        assertEquals("外包装完好", captor.getValue().getQualityNote());
    }

    @Test
    void 质检结论非法拒绝() {
        ReturnOrder submitted = draftReturnOrder();
        submitted.setStatus(ReturnOrderStatus.SUBMITTED.getCode());
        when(returnOrderMapper.selectReturnOrderById(1L)).thenReturn(submitted);
        when(returnItemMapper.selectListByReturnId(1L)).thenReturn(List.of(storedItem("2")));

        ReturnInspectDTO dto = new ReturnInspectDTO();
        ReturnInspectDTO.Item dtoItem = new ReturnInspectDTO.Item();
        dtoItem.setItemId(900L);
        dtoItem.setQualityResult(3);
        dto.setItems(List.of(dtoItem));

        assertThrows(ServiceException.class, () -> returnOrderService.inspect(1L, dto));
        verify(returnItemMapper, never()).updateReturnItem(any(ReturnItem.class));
        verify(returnOrderMapper, never()).updateReturnOrder(any(ReturnOrder.class));
    }

    @Test
    void 质检明细不属于该退货单拒绝() {
        ReturnOrder submitted = draftReturnOrder();
        submitted.setStatus(ReturnOrderStatus.SUBMITTED.getCode());
        when(returnOrderMapper.selectReturnOrderById(1L)).thenReturn(submitted);
        when(returnItemMapper.selectListByReturnId(1L)).thenReturn(List.of(storedItem("2")));

        ReturnInspectDTO dto = new ReturnInspectDTO();
        ReturnInspectDTO.Item dtoItem = new ReturnInspectDTO.Item();
        dtoItem.setItemId(999L);
        dtoItem.setQualityResult(1);
        dto.setItems(List.of(dtoItem));

        ServiceException ex = assertThrows(ServiceException.class, () -> returnOrderService.inspect(1L, dto));
        assertTrue(ex.getMessage().contains("退货明细不存在"));
    }

    @Test
    void 非质检中状态不可质检() {
        when(returnOrderMapper.selectReturnOrderById(1L)).thenReturn(draftReturnOrder());

        ReturnInspectDTO dto = new ReturnInspectDTO();
        ReturnInspectDTO.Item dtoItem = new ReturnInspectDTO.Item();
        dtoItem.setItemId(900L);
        dtoItem.setQualityResult(1);
        dto.setItems(List.of(dtoItem));

        assertThrows(ServiceException.class, () -> returnOrderService.inspect(1L, dto));
        verify(returnItemMapper, never()).updateReturnItem(any(ReturnItem.class));
    }

    // ==================== 删除 ====================

    @Test
    void 已提交不可删除() {
        ReturnOrder submitted = draftReturnOrder();
        submitted.setStatus(ReturnOrderStatus.SUBMITTED.getCode());
        when(returnOrderMapper.selectReturnOrderById(1L)).thenReturn(submitted);

        assertThrows(ServiceException.class, () -> returnOrderService.deleteByIds(new Long[]{1L}));
        verify(returnOrderMapper, never()).deleteReturnOrderById(any());
    }

    @Test
    void 草稿删除走逻辑删除并清明细() {
        when(returnOrderMapper.selectReturnOrderById(1L)).thenReturn(draftReturnOrder());

        returnOrderService.deleteByIds(new Long[]{1L});

        verify(returnItemMapper).deleteByReturnId(1L);
        verify(returnOrderMapper).deleteReturnOrderById(1L); // XML 侧为 is_deleted=1 软删
    }
}

package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.AcceptanceStatus;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.DeliveryScopeType;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.AcceptanceItem;
import com.lin.distribution.domain.AcceptanceRevokeLog;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.DeliverySourceItem;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.AcceptanceUpdateDTO;
import com.lin.distribution.mapper.AcceptanceItemMapper;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.AcceptanceRevokeLogMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliverySourceItemMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
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
import java.util.ArrayList;
import java.util.Arrays;
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
 * 验收单服务测试（DESIGN.md 验收标准 7-9 + S14/T5 验收口径）：
 * 一单一验、后端重算、双向差异必填原因（D-013/G8）、
 * A类总单「配送点×商品」点级展开（§3.2③ 录入即归属）、
 * actual_* 镜像同步（应送占比分摊）、撤销验收（§5.4/D-014 审计快照）。
 */
@ExtendWith(MockitoExtension.class)
class AcceptanceServiceImplTest {

    @Mock
    private AcceptanceMapper acceptanceMapper;
    @Mock
    private AcceptanceItemMapper acceptanceItemMapper;
    @Mock
    private AcceptanceRevokeLogMapper acceptanceRevokeLogMapper;
    @Mock
    private DeliveryOrderMapper deliveryOrderMapper;
    @Mock
    private DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    @Mock
    private DeliverySourceItemMapper deliverySourceItemMapper;
    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private SaleOrderDetailMapper saleOrderDetailMapper;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private AcceptanceServiceImpl acceptanceService;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 17);
    private static final Long CUSTOMER = 100L;
    private static final Long POINT = 101L;
    private static final Long DEPT_A = 201L;
    private static final Long DEPT_B = 202L;
    private static final Long DELIVERY_ID = 500L;
    private static final Long DETAIL_ID = 700L;

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

    private DeliverySourceItem source(Long deptId, Long deliveryDetailId, Long orderDetailId,
                                      String allocated, String price) {
        DeliverySourceItem si = new DeliverySourceItem();
        si.setDeliveryId(DELIVERY_ID);
        si.setDeliveryDetailId(deliveryDetailId);
        si.setSaleOrderId(1000L);
        si.setSaleOrderDetailId(orderDetailId);
        si.setCustomerDeptId(deptId);
        si.setSkuId(11L);
        si.setProductName("白菜");
        si.setAllocatedQuantity(new BigDecimal(allocated));
        si.setUnitPrice(new BigDecimal(price));
        si.setIsDeleted(false);
        return si;
    }

    private AcceptanceItem item(Long id, Long acceptanceId, String delivered, String price) {
        AcceptanceItem item = new AcceptanceItem();
        item.setId(id);
        item.setAcceptanceId(acceptanceId);
        item.setProductName("白菜");
        item.setDeliveredQuantity(new BigDecimal(delivered));
        item.setActualQuantity(new BigDecimal(delivered));
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

    private AcceptanceUpdateDTO singleItemDto(String actual, String reason) {
        AcceptanceUpdateDTO dto = new AcceptanceUpdateDTO();
        dto.setId(1L);
        AcceptanceUpdateDTO.Item dtoItem = new AcceptanceUpdateDTO.Item();
        dtoItem.setId(900L);
        dtoItem.setActualQuantity(actual == null ? null : new BigDecimal(actual));
        dtoItem.setLossReason(reason);
        dto.setItems(Collections.singletonList(dtoItem));
        return dto;
    }

    // ==================== 生成验收单（createByDeliveryOrder） ====================

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
    void A类总单按配送点展开且应送为各点分配之和() {
        DeliveryOrder order = deliveredOrder();
        order.setScopeType(DeliveryScopeType.CUSTOMER_DATE);
        order.setDeliveryPointId(null); // A类总单无单据级配送点
        when(deliveryOrderMapper.selectDeliveryOrderById(DELIVERY_ID)).thenReturn(order);
        when(acceptanceMapper.countByDeliveryOrderId(DELIVERY_ID)).thenReturn(0);
        // 同一送货行被两个配送点分配（生成侧五元组合并保证同一 detail 即同一商品五元组）
        when(deliveryOrderDetailMapper.selectListByDeliveryId(DELIVERY_ID))
                .thenReturn(Collections.singletonList(detail(DETAIL_ID, "2.00", "5")));
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID)).thenReturn(Arrays.asList(
                source(DEPT_A, DETAIL_ID, 9001L, "3", "2.00"),
                source(DEPT_B, DETAIL_ID, 9002L, "2", "2.00")));
        when(bizCodeService.nextDailyCode("acceptance", "YS", 3)).thenReturn("YS20260817001");
        when(acceptanceMapper.insertAcceptance(any(Acceptance.class))).thenAnswer(inv -> {
            inv.getArgument(0, Acceptance.class).setId(1L);
            return 1;
        });

        Acceptance created = acceptanceService.createByDeliveryOrder(DELIVERY_ID);

        ArgumentCaptor<List<AcceptanceItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(acceptanceItemMapper).insertAcceptanceItemBatch(captor.capture());
        List<AcceptanceItem> items = captor.getValue();

        // 「点×商品」两行：dept 维度拆开，delivered=SUM(allocated)
        assertEquals(2, items.size());
        AcceptanceItem rowA = items.get(0);
        assertEquals(DEPT_A, rowA.getCustomerDeptId());
        assertEquals(DETAIL_ID, rowA.getDeliveryItemId());
        assertEquals(0, new BigDecimal("3.00").compareTo(rowA.getDeliveredQuantity()));
        assertEquals(0, new BigDecimal("3.00").compareTo(rowA.getActualQuantity()));
        // 规格/单位/单价快照取自送货行（同一五元组）
        assertEquals(11L, rowA.getSkuId());
        assertEquals("白菜", rowA.getProductName());
        assertEquals("斤", rowA.getProductUnit());
        assertEquals(0, new BigDecimal("2.00").compareTo(rowA.getUnitPrice()));
        assertEquals(0, new BigDecimal("6.00").compareTo(rowA.getActualAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(rowA.getDifferenceQuantity()));
        assertEquals(0, rowA.getSort());

        AcceptanceItem rowB = items.get(1);
        assertEquals(DEPT_B, rowB.getCustomerDeptId());
        assertEquals(0, new BigDecimal("2.00").compareTo(rowB.getDeliveredQuantity()));
        assertEquals(0, new BigDecimal("4.00").compareTo(rowB.getActualAmount()));
        assertEquals(1, rowB.getSort());

        assertEquals(0, new BigDecimal("10.00").compareTo(created.getTotalAmount()));
    }

    @Test
    void A类历史单无来源台账时回退按送货明细行展开() {
        DeliveryOrder order = deliveredOrder();
        order.setScopeType(DeliveryScopeType.CUSTOMER_DATE);
        when(deliveryOrderMapper.selectDeliveryOrderById(DELIVERY_ID)).thenReturn(order);
        when(acceptanceMapper.countByDeliveryOrderId(DELIVERY_ID)).thenReturn(0);
        when(deliveryOrderDetailMapper.selectListByDeliveryId(DELIVERY_ID))
                .thenReturn(Collections.singletonList(detail(DETAIL_ID, "2.00", "5")));
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID)).thenReturn(Collections.emptyList());
        when(bizCodeService.nextDailyCode("acceptance", "YS", 3)).thenReturn("YS20260817001");
        when(acceptanceMapper.insertAcceptance(any(Acceptance.class))).thenAnswer(inv -> {
            inv.getArgument(0, Acceptance.class).setId(1L);
            return 1;
        });

        acceptanceService.createByDeliveryOrder(DELIVERY_ID);

        ArgumentCaptor<List<AcceptanceItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(acceptanceItemMapper).insertAcceptanceItemBatch(captor.capture());
        List<AcceptanceItem> items = captor.getValue();
        // 回退口径：一送货行一验收行，dept 回退单据 delivery_point_id（页面显示"历史无来源"）
        assertEquals(1, items.size());
        assertEquals(DETAIL_ID, items.get(0).getDeliveryItemId());
        assertEquals(POINT, items.get(0).getCustomerDeptId());
        assertEquals(0, new BigDecimal("5.00").compareTo(items.get(0).getDeliveredQuantity()));
    }

    @Test
    void B类明细行配送点取行级dept空则回退单据配送点() {
        when(deliveryOrderMapper.selectDeliveryOrderById(DELIVERY_ID)).thenReturn(deliveredOrder());
        when(acceptanceMapper.countByDeliveryOrderId(DELIVERY_ID)).thenReturn(0);
        DeliveryOrderDetail withDept = detail(700L, "2.00", "5");
        withDept.setCustomerDeptId(DEPT_A);
        DeliveryOrderDetail withoutDept = detail(701L, "3.50", "4"); // 历史单行无 dept
        when(deliveryOrderDetailMapper.selectListByDeliveryId(DELIVERY_ID))
                .thenReturn(Arrays.asList(withDept, withoutDept));
        when(bizCodeService.nextDailyCode("acceptance", "YS", 3)).thenReturn("YS20260817001");
        when(acceptanceMapper.insertAcceptance(any(Acceptance.class))).thenAnswer(inv -> {
            inv.getArgument(0, Acceptance.class).setId(1L);
            return 1;
        });

        acceptanceService.createByDeliveryOrder(DELIVERY_ID);

        ArgumentCaptor<List<AcceptanceItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(acceptanceItemMapper).insertAcceptanceItemBatch(captor.capture());
        List<AcceptanceItem> items = captor.getValue();
        assertEquals(2, items.size());
        assertEquals(DEPT_A, items.get(0).getCustomerDeptId()); // 行级 dept 优先
        assertEquals(POINT, items.get(1).getCustomerDeptId()); // 空回退 delivery_point_id
    }

    // ==================== 草稿录入（updateDraft：双向差异必填原因） ====================

    @Test
    void 录入时实收金额按后端单价重算且超收记超收原因() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem dbItem = item(900L, 1L, "5.00", "2.00");
        when(acceptanceItemMapper.selectAcceptanceItemById(900L)).thenReturn(dbItem);

        Acceptance result = acceptanceService.updateDraft(singleItemDto("6.00", "过磅误差"));

        assertEquals(0, new BigDecimal("12.00").compareTo(result.getTotalAmount()));
        ArgumentCaptor<AcceptanceItem> captor = ArgumentCaptor.forClass(AcceptanceItem.class);
        verify(acceptanceItemMapper).updateAcceptanceItem(captor.capture());
        AcceptanceItem updated = captor.getValue();
        assertEquals(0, new BigDecimal("1.00").compareTo(updated.getDifferenceQuantity()));
        assertEquals(2, updated.getReasonType()); // 正差异=超收
        assertEquals("过磅误差", updated.getLossReason());
        assertEquals(0, new BigDecimal("12.00").compareTo(updated.getActualAmount()));
    }

    @Test
    void 损耗为负必须填写原因() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem dbItem = item(900L, 1L, "5.00", "2.00");
        when(acceptanceItemMapper.selectAcceptanceItemById(900L)).thenReturn(dbItem);

        // 短收 2 未填原因 → 拒绝（D-013/G8 双向必填）
        ServiceException ex = assertThrows(ServiceException.class,
                () -> acceptanceService.updateDraft(singleItemDto("3.00", null)));
        assertTrue(ex.getMessage().contains("短收差异必须填写原因"));
        assertTrue(ex.getMessage().contains("白菜"));
        verify(acceptanceItemMapper, never()).updateAcceptanceItem(any(AcceptanceItem.class));
    }

    @Test
    void 超收差异未填原因拒绝() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem dbItem = item(900L, 1L, "5.00", "2.00");
        when(acceptanceItemMapper.selectAcceptanceItemById(900L)).thenReturn(dbItem);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> acceptanceService.updateDraft(singleItemDto("6.00", "  ")));
        assertTrue(ex.getMessage().contains("超收差异必须填写原因"));
        verify(acceptanceItemMapper, never()).updateAcceptanceItem(any(AcceptanceItem.class));
    }

    @Test
    void 短收差异填写原因后按短收类型落库() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem dbItem = item(900L, 1L, "5.00", "2.00");
        when(acceptanceItemMapper.selectAcceptanceItemById(900L)).thenReturn(dbItem);

        Acceptance result = acceptanceService.updateDraft(singleItemDto("4.00", "运输损耗"));

        assertEquals(0, new BigDecimal("8.00").compareTo(result.getTotalAmount()));
        ArgumentCaptor<AcceptanceItem> captor = ArgumentCaptor.forClass(AcceptanceItem.class);
        verify(acceptanceItemMapper).updateAcceptanceItem(captor.capture());
        AcceptanceItem updated = captor.getValue();
        assertEquals(0, new BigDecimal("-1.00").compareTo(updated.getDifferenceQuantity()));
        assertEquals(1, updated.getReasonType()); // 负差异=短收
        assertEquals("运输损耗", updated.getLossReason());
    }

    @Test
    void 无差异时清空原因与原因类型() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem dbItem = item(900L, 1L, "5.00", "2.00");
        dbItem.setReasonType(1); // 上次短收遗留
        dbItem.setLossReason("运输损耗");
        when(acceptanceItemMapper.selectAcceptanceItemById(900L)).thenReturn(dbItem);

        acceptanceService.updateDraft(singleItemDto("5.00", "误填的原因"));

        ArgumentCaptor<AcceptanceItem> captor = ArgumentCaptor.forClass(AcceptanceItem.class);
        verify(acceptanceItemMapper).updateAcceptanceItem(captor.capture());
        AcceptanceItem updated = captor.getValue();
        assertEquals(0, BigDecimal.ZERO.compareTo(updated.getDifferenceQuantity()));
        assertNull(updated.getReasonType());
        assertNull(updated.getLossReason()); // 无差异清空原因（XML 无条件写该列）
        assertEquals(0, new BigDecimal("10.00").compareTo(updated.getActualAmount()));
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

    // ==================== 提交（submit：IN 回写 + 镜像同步） ====================

    @Test
    void 提交后状态变更且来源订单进入已验收() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        // 历史单（无来源台账）：镜像同步跳过，订单状态回写照常
        when(acceptanceItemMapper.selectListByAcceptanceId(1L))
                .thenReturn(Collections.singletonList(item(900L, 1L, "5.00", "2.00")));
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID)).thenReturn(Collections.emptyList());

        Acceptance result = acceptanceService.submit(1L);

        assertEquals(AcceptanceStatus.SUBMITTED.getCode(), result.getStatus());
        // S14/G3：IN 回写，仅动来源台账命中的订单（同组未进单订单不受影响）
        verify(saleOrderMapper).updateStatusByDeliveryId(
                eq(DELIVERY_ID),
                eq(SaleOrderStatus.DELIVERED.getCode()),
                eq(SaleOrderStatus.ACCEPTED.getCode()));
        verify(saleOrderDetailMapper, never()).updateActualBatch(any(SaleOrderDetail.class));
    }

    @Test
    void 提交时实收按应送占比分摊镜像到订单行() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        // 一条点级验收行：应送 5 实收 4（短收，ratio=0.8）
        AcceptanceItem accItem = item(900L, 1L, "5.00", "2.00");
        accItem.setDeliveryItemId(DETAIL_ID);
        accItem.setActualQuantity(new BigDecimal("4.00"));
        when(acceptanceItemMapper.selectListByAcceptanceId(1L))
                .thenReturn(new ArrayList<>(Collections.singletonList(accItem)));
        // 该送货行的来源分配：订单行 9001 分配 3、订单行 9002 分配 2
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID)).thenReturn(Arrays.asList(
                source(DEPT_A, DETAIL_ID, 9001L, "3", "2.50"),
                source(DEPT_B, DETAIL_ID, 9002L, "2", "2.50")));

        acceptanceService.submit(1L);

        ArgumentCaptor<SaleOrderDetail> captor = ArgumentCaptor.forClass(SaleOrderDetail.class);
        verify(saleOrderDetailMapper, org.mockito.Mockito.times(2)).updateActualBatch(captor.capture());
        List<SaleOrderDetail> mirrors = captor.getAllValues();
        // 行实收 = 分配量 × (实收/应送)：3×0.8=2.40、2×0.8=1.60，单价锁来源行快照
        assertEquals(9001L, mirrors.get(0).getId());
        assertEquals(0, new BigDecimal("2.40").compareTo(mirrors.get(0).getActualNum()));
        assertEquals(0, new BigDecimal("2.50").compareTo(mirrors.get(0).getActualPrice()));
        assertEquals(0, new BigDecimal("6.00").compareTo(mirrors.get(0).getActualAmount()));
        assertEquals(9002L, mirrors.get(1).getId());
        assertEquals(0, new BigDecimal("1.60").compareTo(mirrors.get(1).getActualNum()));
        assertEquals(0, new BigDecimal("4.00").compareTo(mirrors.get(1).getActualAmount()));
    }

    @Test
    void 已提交不可重复提交() {
        Acceptance acceptance = draftAcceptance();
        acceptance.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(acceptance);
        assertThrows(ServiceException.class, () -> acceptanceService.submit(1L));
        verify(saleOrderMapper, never()).updateStatusByDeliveryId(any(), any(), any());
    }

    // ==================== 撤销验收（revoke：审计快照 + 回草稿 + 清镜像） ====================

    private Acceptance submittedAcceptance() {
        Acceptance acceptance = draftAcceptance();
        acceptance.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        return acceptance;
    }

    @Test
    void 撤销验收成功落审计快照并回草稿清镜像() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(submittedAcceptance());
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID)).thenReturn(Arrays.asList(
                source(DEPT_A, DETAIL_ID, 9001L, "3", "2.00"),
                source(DEPT_B, DETAIL_ID, 9002L, "2", "2.00")));
        SaleOrder order = new SaleOrder();
        order.setId(1000L);
        order.setCode("XS20260817001");
        order.setStatus(SaleOrderStatus.ACCEPTED.getCode());
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(1000L)))
                .thenReturn(Collections.singletonList(order));
        AcceptanceItem accItem = item(900L, 1L, "5.00", "2.00");
        when(acceptanceItemMapper.selectListByAcceptanceId(1L))
                .thenReturn(Collections.singletonList(accItem));

        Acceptance result = acceptanceService.revoke(1L, "录入实收数量有误");

        // 验收单回草稿 + 撤销三要素
        assertEquals(AcceptanceStatus.DRAFT.getCode(), result.getStatus());
        assertEquals("录入实收数量有误", result.getRevokeReason());
        assertEquals("system", result.getRevokedBy()); // 无登录上下文回落
        assertNotNull(result.getRevokedTime());

        // 审计快照（主表+明细 JSON）
        ArgumentCaptor<AcceptanceRevokeLog> logCaptor = ArgumentCaptor.forClass(AcceptanceRevokeLog.class);
        verify(acceptanceRevokeLogMapper).insertAcceptanceRevokeLog(logCaptor.capture());
        AcceptanceRevokeLog revokeLog = logCaptor.getValue();
        assertEquals(1L, revokeLog.getAcceptanceId());
        assertEquals("录入实收数量有误", revokeLog.getReason());
        assertTrue(revokeLog.getSnapshotJson().contains("YS20260817001"));
        assertTrue(revokeLog.getSnapshotJson().contains("白菜"));

        // 主表回草稿
        ArgumentCaptor<Acceptance> accCaptor = ArgumentCaptor.forClass(Acceptance.class);
        verify(acceptanceMapper).updateAcceptance(accCaptor.capture());
        assertEquals(AcceptanceStatus.DRAFT.getCode(), accCaptor.getValue().getStatus());

        // 来源订单 ACCEPTED→DELIVERED（IN 回写）
        verify(saleOrderMapper).updateStatusByDeliveryId(
                eq(DELIVERY_ID),
                eq(SaleOrderStatus.ACCEPTED.getCode()),
                eq(SaleOrderStatus.DELIVERED.getCode()));

        // 清空来源订单行 actual_* 镜像
        verify(saleOrderDetailMapper).clearActualByOrderId(1000L);
    }

    @Test
    void 来源订单已结算禁止撤销验收() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(submittedAcceptance());
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID)).thenReturn(Collections.singletonList(
                source(DEPT_A, DETAIL_ID, 9001L, "3", "2.00")));
        SaleOrder settled = new SaleOrder();
        settled.setId(1000L);
        settled.setCode("XS20260817001");
        settled.setStatus(SaleOrderStatus.SETTLED.getCode());
        when(saleOrderMapper.selectSaleOrderByIdIn(anyList())).thenReturn(Collections.singletonList(settled));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> acceptanceService.revoke(1L, "发现差异"));
        assertTrue(ex.getMessage().contains("XS20260817001"));
        verify(acceptanceRevokeLogMapper, never()).insertAcceptanceRevokeLog(any(AcceptanceRevokeLog.class));
        verify(saleOrderMapper, never()).updateStatusByDeliveryId(any(), any(), any());
        verify(saleOrderDetailMapper, never()).clearActualByOrderId(any());
    }

    @Test
    void 草稿状态不可撤销验收() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        assertThrows(ServiceException.class, () -> acceptanceService.revoke(1L, "误操作"));
        verify(acceptanceRevokeLogMapper, never()).insertAcceptanceRevokeLog(any(AcceptanceRevokeLog.class));
        verify(saleOrderMapper, never()).updateStatusByDeliveryId(any(), any(), any());
    }

    @Test
    void 撤销原因必填() {
        assertThrows(ServiceException.class, () -> acceptanceService.revoke(1L, " "));
        verify(acceptanceMapper, never()).selectAcceptanceById(any());
        verify(acceptanceRevokeLogMapper, never()).insertAcceptanceRevokeLog(any(AcceptanceRevokeLog.class));
    }

    // ==================== 删除 ====================

    @Test
    void 已提交不可删除() {
        Acceptance acceptance = draftAcceptance();
        acceptance.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(acceptance);
        assertThrows(ServiceException.class, () -> acceptanceService.deleteByIds(new Long[]{1L}));
        verify(acceptanceMapper, never()).deleteAcceptanceByIds(any(Long[].class));
    }
}

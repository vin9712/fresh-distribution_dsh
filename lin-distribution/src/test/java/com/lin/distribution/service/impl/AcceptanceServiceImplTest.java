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
import com.lin.distribution.dto.AcceptanceQuickAcceptDTO;
import com.lin.distribution.dto.AcceptanceUpdateDTO;
import com.lin.distribution.mapper.AcceptanceItemMapper;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.AcceptanceRevokeLogMapper;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliverySourceItemMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.vo.AcceptanceByOrderVO;
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
import static org.mockito.ArgumentMatchers.anyString;
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
    private CustomerDeptMapper customerDeptMapper;
    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private SaleOrderDetailMapper saleOrderDetailMapper;
    @Mock
    private com.lin.distribution.mapper.MonthSettlementMapper monthSettlementMapper;
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
    void 全部拒收不填原因允许且记短收类型() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem dbItem = item(900L, 1L, "5.00", "2.00");
        when(acceptanceItemMapper.selectAcceptanceItemById(900L)).thenReturn(dbItem);

        // OA 定稿：原因选填，全部拒收也允许不填，类型仍自动记录
        Acceptance result = acceptanceService.updateDraft(singleItemDto("0.00", null));

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalAmount()));
        ArgumentCaptor<AcceptanceItem> captor = ArgumentCaptor.forClass(AcceptanceItem.class);
        verify(acceptanceItemMapper).updateAcceptanceItem(captor.capture());
        assertEquals(1, captor.getValue().getReasonType());
        assertNull(captor.getValue().getLossReason());
    }

    @Test
    void 部分短收未填原因允许保存且记短收类型() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem dbItem = item(900L, 1L, "5.00", "2.00");
        when(acceptanceItemMapper.selectAcceptanceItemById(900L)).thenReturn(dbItem);

        // 部分短收（0<3<5）：原因可不填（蓝图 W0-2.6 建议不强制），仍记短收类型
        Acceptance result = acceptanceService.updateDraft(singleItemDto("3.00", null));

        assertEquals(0, new BigDecimal("6.00").compareTo(result.getTotalAmount()));
        ArgumentCaptor<AcceptanceItem> captor = ArgumentCaptor.forClass(AcceptanceItem.class);
        verify(acceptanceItemMapper).updateAcceptanceItem(captor.capture());
        AcceptanceItem updated = captor.getValue();
        assertEquals(0, new BigDecimal("-2.00").compareTo(updated.getDifferenceQuantity()));
        assertEquals(1, updated.getReasonType()); // 负差异=短收
        assertNull(updated.getLossReason());       // 未填原因
    }

    @Test
    void 超收未填原因允许且记超收类型() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem dbItem = item(900L, 1L, "5.00", "2.00");
        when(acceptanceItemMapper.selectAcceptanceItemById(900L)).thenReturn(dbItem);

        // OA 定稿：原因选填，超收不填也允许保存，类型仍自动记录
        Acceptance result = acceptanceService.updateDraft(singleItemDto("6.00", "  "));

        assertEquals(0, new BigDecimal("12.00").compareTo(result.getTotalAmount()));
        ArgumentCaptor<AcceptanceItem> captor = ArgumentCaptor.forClass(AcceptanceItem.class);
        verify(acceptanceItemMapper).updateAcceptanceItem(captor.capture());
        assertEquals(2, captor.getValue().getReasonType());
        assertNull(captor.getValue().getLossReason());
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

    /** D-055 点单验收（无送货单）：提交时实收 1:1 回写订单明细镜像，来源订单 CONFIRMED→ACCEPTED */
    @Test
    void 点单验收提交回写实收镜像与订单状态() {
        Acceptance point = draftAcceptance();
        point.setDeliveryOrderId(null);
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(point);
        AcceptanceItem it = item(900L, 1L, "5.00", "2.00");
        it.setSaleOrderDetailId(9001L);
        it.setActualQuantity(new BigDecimal("4.00"));
        it.setActualAmount(new BigDecimal("8.00"));
        it.setLossReason("客户临时减量");
        when(acceptanceItemMapper.selectListByAcceptanceId(1L)).thenReturn(Collections.singletonList(it));
        SaleOrderDetail detail = new SaleOrderDetail();
        detail.setId(9001L);
        detail.setOrderId(1000L);
        when(saleOrderDetailMapper.selectByIdIn(Collections.singletonList(9001L)))
                .thenReturn(Collections.singletonList(detail));

        Acceptance result = acceptanceService.submit(1L);

        assertEquals(AcceptanceStatus.SUBMITTED.getCode(), result.getStatus());
        ArgumentCaptor<SaleOrderDetail> captor = ArgumentCaptor.forClass(SaleOrderDetail.class);
        verify(saleOrderDetailMapper).updateActualBatch(captor.capture());
        SaleOrderDetail mirror = captor.getValue();
        assertEquals(9001L, mirror.getId());
        assertEquals(0, new BigDecimal("4.00").compareTo(mirror.getActualNum()));
        assertEquals(0, new BigDecimal("2.00").compareTo(mirror.getActualPrice()));
        assertEquals(0, new BigDecimal("8.00").compareTo(mirror.getActualAmount()));
        assertEquals("客户临时减量", mirror.getLossReason());
        verify(saleOrderMapper).updateStatusByIds(Collections.singletonList(1000L),
                SaleOrderStatus.CONFIRMED.getCode(), SaleOrderStatus.ACCEPTED.getCode());
        // 点单口径不得走送货单 IN 回写，也不走 source_item 占比分摊
        verify(saleOrderMapper, never()).updateStatusByDeliveryId(any(), any(), any());
        verify(deliverySourceItemMapper, never()).selectListByDeliveryId(any());
    }

    /** D-055 点单验收撤销：来源订单 ACCEPTED→CONFIRMED（回到未配送可继续改单）并清镜像 */
    @Test
    void 点单验收撤销回退订单状态并清镜像() {
        Acceptance point = submittedAcceptance();
        point.setDeliveryOrderId(null);
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(point);
        AcceptanceItem it = item(900L, 1L, "5.00", "2.00");
        it.setSaleOrderDetailId(9001L);
        when(acceptanceItemMapper.selectListByAcceptanceId(1L)).thenReturn(Collections.singletonList(it));
        SaleOrderDetail detail = new SaleOrderDetail();
        detail.setId(9001L);
        detail.setOrderId(1000L);
        when(saleOrderDetailMapper.selectByIdIn(Collections.singletonList(9001L)))
                .thenReturn(Collections.singletonList(detail));
        SaleOrder order = new SaleOrder();
        order.setId(1000L);
        order.setCode("XD202609030001");
        order.setStatus(SaleOrderStatus.ACCEPTED.getCode());
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(1000L)))
                .thenReturn(Collections.singletonList(order));

        Acceptance result = acceptanceService.revoke(1L, "现场数量复核有误");

        assertEquals(AcceptanceStatus.DRAFT.getCode(), result.getStatus());
        verify(saleOrderMapper).updateStatusByIds(Collections.singletonList(1000L),
                SaleOrderStatus.ACCEPTED.getCode(), SaleOrderStatus.CONFIRMED.getCode());
        verify(saleOrderDetailMapper).clearActualByOrderId(1000L);
        verify(saleOrderMapper, never()).updateStatusByDeliveryId(any(), any(), any());
    }

    /** D-055 点单验收：来源订单已结算时禁止撤销（结算依据链不可断） */
    @Test
    void 点单验收来源订单已结算禁止撤销() {
        Acceptance point = submittedAcceptance();
        point.setDeliveryOrderId(null);
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(point);
        AcceptanceItem it = item(900L, 1L, "5.00", "2.00");
        it.setSaleOrderDetailId(9001L);
        when(acceptanceItemMapper.selectListByAcceptanceId(1L)).thenReturn(Collections.singletonList(it));
        SaleOrderDetail detail = new SaleOrderDetail();
        detail.setId(9001L);
        detail.setOrderId(1000L);
        when(saleOrderDetailMapper.selectByIdIn(Collections.singletonList(9001L)))
                .thenReturn(Collections.singletonList(detail));
        SaleOrder settled = new SaleOrder();
        settled.setId(1000L);
        settled.setCode("XD202609030001");
        settled.setStatus(SaleOrderStatus.SETTLED.getCode());
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(1000L)))
                .thenReturn(Collections.singletonList(settled));

        ServiceException ex = assertThrows(ServiceException.class, () -> acceptanceService.revoke(1L, "要改数"));
        assertTrue(ex.getMessage().contains("XD202609030001"));
        verify(saleOrderMapper, never()).updateStatusByIds(any(), any(), any());
        verify(acceptanceRevokeLogMapper, never()).insertAcceptanceRevokeLog(any());
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

    // ==================== 去验收定位（locateBySaleOrder，S14 §6.1） ====================

    @Test
    void 去验收定位命中送货单与已有验收单() {
        DeliverySourceItem si = source(DEPT_A, DETAIL_ID, 8001L, "5", "2.50");
        si.setDeliveryId(DELIVERY_ID);
        when(deliverySourceItemMapper.selectDeliverySourceItemList(any(DeliverySourceItem.class)))
                .thenReturn(List.of(si));
        when(deliveryOrderMapper.selectListByIds(any())).thenReturn(List.of(deliveredOrder()));
        when(acceptanceMapper.selectAcceptanceList(any(Acceptance.class)))
                .thenReturn(List.of(draftAcceptance()));

        AcceptanceByOrderVO vo = acceptanceService.locateBySaleOrder(1000L);

        assertEquals(1000L, vo.getOrderId());
        assertEquals(DELIVERY_ID, vo.getDeliveryId());
        assertEquals(DeliveryOrderStatus.DELIVERED.getCode(), vo.getDeliveryStatus());
        assertTrue(vo.getHasAcceptance());
        assertEquals(1L, vo.getAcceptanceId());
        assertEquals("YS20260817001", vo.getAcceptanceCode());
    }

    @Test
    void 去验收定位无验收单时带送货单引导创建草稿() {
        DeliverySourceItem si = source(DEPT_A, DETAIL_ID, 8001L, "5", "2.50");
        si.setDeliveryId(DELIVERY_ID);
        when(deliverySourceItemMapper.selectDeliverySourceItemList(any(DeliverySourceItem.class)))
                .thenReturn(List.of(si));
        when(deliveryOrderMapper.selectListByIds(any())).thenReturn(List.of(deliveredOrder()));
        when(acceptanceMapper.selectAcceptanceList(any(Acceptance.class))).thenReturn(Collections.emptyList());

        AcceptanceByOrderVO vo = acceptanceService.locateBySaleOrder(1000L);

        assertEquals(DELIVERY_ID, vo.getDeliveryId());
        assertEquals(Boolean.FALSE, vo.getHasAcceptance());
        assertNull(vo.getAcceptanceId());
    }

    @Test
    void 去验收定位历史单回退送货明细行order_id() {
        when(deliverySourceItemMapper.selectDeliverySourceItemList(any(DeliverySourceItem.class)))
                .thenReturn(Collections.emptyList());
        DeliveryOrderDetail detail = detail(DETAIL_ID, "2.50", "5");
        detail.setDeliveryId(DELIVERY_ID);
        detail.setOrderId(1000L);
        when(deliveryOrderDetailMapper.selectListByOrderIdIn(any())).thenReturn(List.of(detail));
        when(deliveryOrderMapper.selectListByIds(any())).thenReturn(List.of(deliveredOrder()));
        when(acceptanceMapper.selectAcceptanceList(any(Acceptance.class))).thenReturn(Collections.emptyList());

        AcceptanceByOrderVO vo = acceptanceService.locateBySaleOrder(1000L);

        assertEquals(DELIVERY_ID, vo.getDeliveryId());
        verify(deliverySourceItemMapper).selectDeliverySourceItemList(any(DeliverySourceItem.class));
    }

    @Test
    void 去验收定位排除已作废送货单() {
        when(deliverySourceItemMapper.selectDeliverySourceItemList(any(DeliverySourceItem.class)))
                .thenReturn(Collections.emptyList());
        DeliveryOrderDetail detail = detail(DETAIL_ID, "2.50", "5");
        detail.setDeliveryId(DELIVERY_ID);
        detail.setOrderId(1000L);
        when(deliveryOrderDetailMapper.selectListByOrderIdIn(any())).thenReturn(List.of(detail));
        DeliveryOrder voided = deliveredOrder();
        voided.setStatus(DeliveryOrderStatus.VOIDED.getCode());
        when(deliveryOrderMapper.selectListByIds(any())).thenReturn(List.of(voided));

        AcceptanceByOrderVO vo = acceptanceService.locateBySaleOrder(1000L);

        assertNull(vo.getDeliveryId());
        verify(acceptanceMapper, never()).selectAcceptanceList(any(Acceptance.class));
    }

    @Test
    void 去验收定位未进入送货单时仅回显订单ID() {
        when(deliverySourceItemMapper.selectDeliverySourceItemList(any(DeliverySourceItem.class)))
                .thenReturn(Collections.emptyList());
        when(deliveryOrderDetailMapper.selectListByOrderIdIn(any())).thenReturn(Collections.emptyList());

        AcceptanceByOrderVO vo = acceptanceService.locateBySaleOrder(1000L);

        assertEquals(1000L, vo.getOrderId());
        assertNull(vo.getDeliveryId());
        assertEquals(Boolean.FALSE, vo.getHasAcceptance());
        verify(deliveryOrderMapper, never()).selectListByIds(any());
    }

    @Test
    void 去验收定位补充单场景优先命中已有验收单的最新一张() {
        DeliverySourceItem siOld = source(DEPT_A, DETAIL_ID, 8001L, "5", "2.50");
        siOld.setDeliveryId(500L);
        DeliverySourceItem siNew = source(DEPT_A, DETAIL_ID, 8001L, "5", "2.50");
        siNew.setDeliveryId(501L);
        when(deliverySourceItemMapper.selectDeliverySourceItemList(any(DeliverySourceItem.class)))
                .thenReturn(List.of(siNew, siOld));
        DeliveryOrder oldOrder = deliveredOrder();
        oldOrder.setId(500L);
        DeliveryOrder newOrder = deliveredOrder();
        newOrder.setId(501L);
        when(deliveryOrderMapper.selectListByIds(any())).thenReturn(List.of(oldOrder, newOrder));
        // 先查 501（最新）即命中验收单
        when(acceptanceMapper.selectAcceptanceList(any(Acceptance.class)))
                .thenReturn(List.of(draftAcceptance()));

        AcceptanceByOrderVO vo = acceptanceService.locateBySaleOrder(1000L);

        assertEquals(501L, vo.getDeliveryId());
        assertEquals(List.of(500L, 501L), vo.getDeliveryIds());
        assertTrue(vo.getHasAcceptance());
    }

    /* ========== T7 第二轮：明细列表回填 来源对照（验收页共用） ========== */

    /** 来源对照回填：按 (delivery_detail_id, customer_dept_id) 匹配 source_item，订单号批量回填 */
    @Test
    void 明细列表回填来源对照() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem itemA = item(900L, 1L, "5.00", "2.50");
        itemA.setDeliveryItemId(DETAIL_ID);
        itemA.setCustomerDeptId(DEPT_A);
        when(acceptanceItemMapper.selectListByAcceptanceId(1L)).thenReturn(List.of(itemA));

        DeliverySourceItem si = source(DEPT_A, DETAIL_ID, 8001L, "5", "2.50");
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID)).thenReturn(List.of(si));
        SaleOrder order = new SaleOrder();
        order.setId(1000L);
        order.setCode("XS20260817001");
        when(saleOrderMapper.selectSaleOrderByIdIn(List.of(1000L))).thenReturn(List.of(order));

        List<AcceptanceItem> items = acceptanceService.selectItemListByAcceptanceId(1L);

        assertEquals(1, items.get(0).getSources().size());
        assertEquals("XS20260817001", items.get(0).getSources().get(0).getOrderCode());
        assertEquals(0, new BigDecimal("5").compareTo(items.get(0).getSources().get(0).getAllocatedQuantity()));
        assertEquals(0, new BigDecimal("2.50").compareTo(items.get(0).getSources().get(0).getUnitPrice()));
    }

    /** 历史单无 source_item：sources 不回填（null→前端展示历史数据） */
    @Test
    void 明细列表历史单无来源对照() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem itemA = item(900L, 1L, "5.00", "2.50");
        itemA.setDeliveryItemId(DETAIL_ID);
        itemA.setCustomerDeptId(DEPT_A);
        when(acceptanceItemMapper.selectListByAcceptanceId(1L)).thenReturn(List.of(itemA));
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID)).thenReturn(List.of());

        List<AcceptanceItem> items = acceptanceService.selectItemListByAcceptanceId(1L);

        assertNull(items.get(0).getSources());
    }

    /** 多来源（同点跨订单合并）回填：两来源行都附到同一验收行，按分配行序返回 */
    @Test
    void 明细列表多来源合并行全部回填() {
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draftAcceptance());
        AcceptanceItem itemA = item(900L, 1L, "10.00", "2.50");
        itemA.setDeliveryItemId(DETAIL_ID);
        itemA.setCustomerDeptId(DEPT_A);
        when(acceptanceItemMapper.selectListByAcceptanceId(1L)).thenReturn(List.of(itemA));

        DeliverySourceItem si1 = source(DEPT_A, DETAIL_ID, 8001L, "6", "2.50");
        si1.setSaleOrderId(1000L);
        DeliverySourceItem si2 = source(DEPT_A, DETAIL_ID, 8002L, "4", "2.50");
        si2.setSaleOrderId(1001L);
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID)).thenReturn(List.of(si1, si2));

        SaleOrder o1 = new SaleOrder();
        o1.setId(1000L);
        o1.setCode("XS001");
        SaleOrder o2 = new SaleOrder();
        o2.setId(1001L);
        o2.setCode("XS002");
        when(saleOrderMapper.selectSaleOrderByIdIn(anyList())).thenReturn(List.of(o1, o2));

        List<AcceptanceItem> items = acceptanceService.selectItemListByAcceptanceId(1L);

        assertEquals(2, items.get(0).getSources().size());
        assertEquals("XS001", items.get(0).getSources().get(0).getOrderCode());
        assertEquals("XS002", items.get(0).getSources().get(1).getOrderCode());
    }

    // ==================== AC-1/AC-2/AC-3 验收=客户日维度（订单明细视角） ====================

    @Test
    void 客户日验收_应送行来自全部订单明细且默认实收等于应送() {
        when(acceptanceMapper.selectByCustomerDate(10L, LocalDate.of(2026, 9, 1))).thenReturn(null);
        SaleOrderDetail normal = new SaleOrderDetail();
        normal.setId(1001L);
        normal.setCustomerDeptId(2L);
        normal.setSkuId(88L);
        normal.setProductName("白菜");
        normal.setProductSpec("");
        normal.setProductUnit("斤");
        normal.setNum(new BigDecimal("5"));
        normal.setProductPrice(new BigDecimal("2.00"));
        normal.setChangeType(0);
        SaleOrderDetail supplement = new SaleOrderDetail();
        supplement.setId(1002L);
        supplement.setCustomerDeptId(2L);
        supplement.setSkuId(88L);
        supplement.setProductName("白菜");
        supplement.setProductSpec("");
        supplement.setProductUnit("斤");
        supplement.setNum(new BigDecimal("3"));
        supplement.setProductPrice(new BigDecimal("2.00"));
        supplement.setChangeType(1);
        supplement.setActualNum(new BigDecimal("3"));
        when(saleOrderDetailMapper.selectValidByCustomerDateForView(10L, LocalDate.of(2026, 9, 1)))
                .thenReturn(new ArrayList<>(List.of(normal, supplement)));
        when(bizCodeService.nextDailyCode(eq("acceptance"), anyString(), eq(3))).thenReturn("YS20260901001");

        Acceptance acceptance = acceptanceService.createByCustomerDate(10L, LocalDate.of(2026, 9, 1));

        assertEquals("YS20260901001", acceptance.getCode());
        assertEquals(LocalDate.of(2026, 9, 1), acceptance.getDeliveryDate());
        assertNull(acceptance.getDeliveryPointId(), "客户日单表头不挂配送点");
        // 批量插入的明细行：应送=订单 num；加单行默认实收=actual_num；行带订单明细引用
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(acceptanceItemMapper).insertAcceptanceItemBatch(captor.capture());
        List<AcceptanceItem> items = captor.getValue();
        assertEquals(2, items.size());
        assertEquals(Long.valueOf(1001L), items.get(0).getSaleOrderDetailId());
        assertEquals(0, new BigDecimal("5").compareTo(items.get(0).getDeliveredQuantity()));
        assertEquals(0, new BigDecimal("5").compareTo(items.get(0).getActualQuantity()));
        assertEquals(0, new BigDecimal("3").compareTo(items.get(1).getActualQuantity()), "加单行默认实收=actual_num");
    }

    @Test
    void 客户日验收_退货标记行应送实收均为零() {
        when(acceptanceMapper.selectByCustomerDate(10L, LocalDate.of(2026, 9, 1))).thenReturn(null);
        SaleOrderDetail returned = new SaleOrderDetail();
        returned.setId(2001L);
        returned.setCustomerDeptId(2L);
        returned.setSkuId(99L);
        returned.setProductName("土豆");
        returned.setProductSpec("");
        returned.setProductUnit("斤");
        returned.setNum(new BigDecimal("4"));
        returned.setProductPrice(new BigDecimal("3.50"));
        returned.setChangeType(3);
        when(saleOrderDetailMapper.selectValidByCustomerDateForView(10L, LocalDate.of(2026, 9, 1)))
                .thenReturn(new ArrayList<>(List.of(returned)));
        when(bizCodeService.nextDailyCode(eq("acceptance"), anyString(), eq(3))).thenReturn("YS20260901002");

        Acceptance acceptance = acceptanceService.createByCustomerDate(10L, LocalDate.of(2026, 9, 1));

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(acceptanceItemMapper).insertAcceptanceItemBatch(captor.capture());
        List<AcceptanceItem> items = captor.getValue();
        assertEquals(0, BigDecimal.ZERO.compareTo(items.get(0).getDeliveredQuantity()), "退货行应送=0");
        assertEquals(0, BigDecimal.ZERO.compareTo(items.get(0).getActualQuantity()), "退货行实收=0");
    }

    @Test
    void 客户日验收_重复建单拦截并带已有单号() {
        Acceptance existing = new Acceptance();
        existing.setId(9L);
        existing.setCode("YS20260901001");
        when(acceptanceMapper.selectByCustomerDate(10L, LocalDate.of(2026, 9, 1))).thenReturn(existing);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> acceptanceService.createByCustomerDate(10L, LocalDate.of(2026, 9, 1)));
        assertTrue(ex.getMessage().contains("YS20260901001"), "提示须带已有验收单号");
        verify(acceptanceItemMapper, never()).insertAcceptanceItemBatch(anyList());
    }

    @Test
    void 去验收定位_订单视角命中客户日验收单() {
        SaleOrder order = new SaleOrder();
        order.setId(1000L);
        order.setCustomerId(10L);
        order.setDeliveryDate(LocalDate.of(2026, 9, 1));
        when(saleOrderMapper.selectSaleOrderById(1000L)).thenReturn(order);
        Acceptance hit = new Acceptance();
        hit.setId(77L);
        hit.setCode("YS20260901001");
        hit.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        when(acceptanceMapper.selectByCustomerDate(10L, LocalDate.of(2026, 9, 1))).thenReturn(hit);

        AcceptanceByOrderVO vo = acceptanceService.locateBySaleOrder(1000L);

        assertEquals(Long.valueOf(10L), vo.getCustomerId());
        assertEquals("2026-09-01", vo.getDeliveryDate());
        assertEquals(Boolean.TRUE, vo.getHasAcceptance());
        assertEquals(Long.valueOf(77L), vo.getAcceptanceId());
        assertNull(vo.getDeliveryId(), "订单视角命中时不再反查送货单");
        verify(deliverySourceItemMapper, never()).selectDeliverySourceItemList(any());
    }

    @Test
    void 去验收定位_订单视角无单且无历史痕迹时引导建草稿() {
        SaleOrder order = new SaleOrder();
        order.setId(1000L);
        order.setCustomerId(10L);
        order.setDeliveryDate(LocalDate.of(2026, 9, 1));
        when(saleOrderMapper.selectSaleOrderById(1000L)).thenReturn(order);
        when(acceptanceMapper.selectByCustomerDate(10L, LocalDate.of(2026, 9, 1))).thenReturn(null);
        when(deliverySourceItemMapper.selectDeliverySourceItemList(any(DeliverySourceItem.class)))
                .thenReturn(Collections.emptyList());
        when(deliveryOrderDetailMapper.selectListByOrderIdIn(any())).thenReturn(Collections.emptyList());

        AcceptanceByOrderVO vo = acceptanceService.locateBySaleOrder(1000L);

        assertEquals(Long.valueOf(10L), vo.getCustomerId());
        assertEquals("2026-09-01", vo.getDeliveryDate());
        assertEquals(Boolean.FALSE, vo.getHasAcceptance());
        assertNull(vo.getDeliveryId());
    }

    @Test
    void 去验收定位_新流程订单有历史送货单痕迹时回退送货单视角() {
        SaleOrder order = new SaleOrder();
        order.setId(1000L);
        order.setCustomerId(10L);
        order.setDeliveryDate(LocalDate.of(2026, 9, 1));
        when(saleOrderMapper.selectSaleOrderById(1000L)).thenReturn(order);
        when(acceptanceMapper.selectByCustomerDate(10L, LocalDate.of(2026, 9, 1))).thenReturn(null);
        // 历史痕迹：source_item 台账命中
        DeliverySourceItem si = source(DEPT_A, DETAIL_ID, 8001L, "5", "2.50");
        si.setDeliveryId(500L);
        when(deliverySourceItemMapper.selectDeliverySourceItemList(any(DeliverySourceItem.class)))
                .thenReturn(List.of(si));
        DeliveryOrder delivery = deliveredOrder();
        when(deliveryOrderMapper.selectListByIds(anyList())).thenReturn(List.of(delivery));
        when(acceptanceMapper.selectAcceptanceList(any(Acceptance.class))).thenReturn(Collections.emptyList());

        AcceptanceByOrderVO vo = acceptanceService.locateBySaleOrder(1000L);

        assertEquals(Long.valueOf(500L), vo.getDeliveryId(), "无客户日验收单但有历史送货单 → 回退送货单视角");
        assertEquals(Boolean.FALSE, vo.getHasAcceptance());
    }

    // ==================== OA：订单维度验收（《订单页一键验收链路设计》） ====================

    private SaleOrder confirmedOrder() {
        SaleOrder order = new SaleOrder();
        order.setId(1000L);
        order.setCode("XS20260901001");
        order.setCustomerId(10L);
        order.setCustomerDeptId(2L);
        order.setDeliveryDate(LocalDate.of(2026, 9, 1));
        order.setStatus(SaleOrderStatus.CONFIRMED.getCode());
        return order;
    }

    private SaleOrderDetail orderDetail(Long id, String num, String price, int changeType) {
        SaleOrderDetail d = new SaleOrderDetail();
        d.setId(id);
        d.setOrderId(1000L);
        d.setCustomerDeptId(2L);
        d.setSkuId(88L);
        d.setProductName("白菜");
        d.setProductSpec("");
        d.setProductUnit("斤");
        d.setNum(new BigDecimal(num));
        d.setProductPrice(new BigDecimal(price));
        d.setChangeType(changeType);
        return d;
    }

    private Acceptance orderAcceptance(Long id, Integer status) {
        Acceptance acc = new Acceptance();
        acc.setId(id);
        acc.setCode("YS20260901001");
        acc.setSaleOrderId(1000L);
        acc.setCustomerId(10L);
        acc.setDeliveryDate(LocalDate.of(2026, 9, 1));
        acc.setAcceptDate(LocalDate.of(2026, 9, 1));
        acc.setStatus(status);
        return acc;
    }

    @Test
    void 订单验收_建单应送行来自单张订单明细且标记行规则一致() {
        when(saleOrderMapper.selectSaleOrderById(1000L)).thenReturn(confirmedOrder());
        when(acceptanceMapper.selectBySaleOrder(1000L)).thenReturn(null);
        SaleOrderDetail normal = orderDetail(1001L, "5", "2.00", 0);
        SaleOrderDetail supplement = orderDetail(1002L, "3", "2.00", 1);
        supplement.setActualNum(new BigDecimal("3"));
        SaleOrderDetail returned = orderDetail(1003L, "4", "3.50", 3);
        when(saleOrderDetailMapper.selectValidByOrderIdForView(1000L))
                .thenReturn(new ArrayList<>(List.of(normal, supplement, returned)));
        when(bizCodeService.nextDailyCode(eq("acceptance"), anyString(), eq(3))).thenReturn("YS20260901001");

        Acceptance acceptance = acceptanceService.createByOrder(1000L);

        assertEquals("YS20260901001", acceptance.getCode());
        assertEquals(Long.valueOf(1000L), acceptance.getSaleOrderId(), "表头挂订单维度");
        assertEquals(Long.valueOf(10L), acceptance.getCustomerId());
        assertEquals(LocalDate.of(2026, 9, 1), acceptance.getAcceptDate(), "缺省验收日期=配送日期");

        ArgumentCaptor<Acceptance> accCaptor = ArgumentCaptor.forClass(Acceptance.class);
        verify(acceptanceMapper).insertAcceptance(accCaptor.capture());
        assertEquals(Long.valueOf(1000L), accCaptor.getValue().getSaleOrderId());

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(acceptanceItemMapper).insertAcceptanceItemBatch(captor.capture());
        List<AcceptanceItem> items = captor.getValue();
        assertEquals(3, items.size());
        assertEquals(Long.valueOf(1001L), items.get(0).getSaleOrderDetailId());
        assertEquals(0, new BigDecimal("5").compareTo(items.get(0).getActualQuantity()), "普通行默认实收=应送");
        assertEquals(0, new BigDecimal("3").compareTo(items.get(1).getActualQuantity()), "加单行默认实收=actual_num 镜像");
        assertEquals(0, BigDecimal.ZERO.compareTo(items.get(2).getDeliveredQuantity()), "退货行应送=0");
        assertEquals(0, BigDecimal.ZERO.compareTo(items.get(2).getActualQuantity()), "退货行实收=0");
        // 总额 = 5×2 + 3×2 + 0 = 16.00
        assertEquals(0, new BigDecimal("16.00").compareTo(accCaptor.getValue().getTotalAmount()));
    }

    @Test
    void 订单验收_一订单一验已提交拒绝() {
        when(saleOrderMapper.selectSaleOrderById(1000L)).thenReturn(confirmedOrder());
        when(acceptanceMapper.selectBySaleOrder(1000L)).thenReturn(orderAcceptance(9L, AcceptanceStatus.SUBMITTED.getCode()));

        ServiceException ex = assertThrows(ServiceException.class, () -> acceptanceService.createByOrder(1000L));
        assertTrue(ex.getMessage().contains("YS20260901001"));
        verify(acceptanceItemMapper, never()).insertAcceptanceItemBatch(anyList());
    }

    @Test
    void 订单验收_已有草稿幂等返回并同步缺失行() {
        when(saleOrderMapper.selectSaleOrderById(1000L)).thenReturn(confirmedOrder());
        Acceptance draft = orderAcceptance(5L, AcceptanceStatus.DRAFT.getCode());
        when(acceptanceMapper.selectBySaleOrder(1000L)).thenReturn(draft);
        when(acceptanceMapper.selectAcceptanceById(5L)).thenReturn(draft);
        // 订单当前明细：只有行 1001（验收单中也已有）→ 无缺失行，幂等返回
        when(saleOrderDetailMapper.selectValidByOrderIdForView(1000L))
                .thenReturn(new ArrayList<>(List.of(orderDetail(1001L, "5", "2.00", 0))));
        AcceptanceItem existing = item(501L, 5L, "5.00", "2.00");
        existing.setSaleOrderDetailId(1001L);
        when(acceptanceItemMapper.selectListByAcceptanceId(5L)).thenReturn(List.of(existing));

        Acceptance result = acceptanceService.createByOrder(1000L);

        assertEquals(Long.valueOf(5L), result.getId(), "已有草稿直接返回");
        verify(acceptanceItemMapper, never()).insertAcceptanceItemBatch(anyList());
    }

    @Test
    void 订单验收_同步缺失行只补新行不覆盖已录实收且退货行归零() {
        Acceptance draft = orderAcceptance(5L, AcceptanceStatus.DRAFT.getCode());
        when(acceptanceMapper.selectAcceptanceById(5L)).thenReturn(draft);
        // 行1001 后来被标退货（原行已在验收单中）；行1002 验收中途新增的加单行
        SaleOrderDetail nowReturned = orderDetail(1001L, "5", "2.00", 3);
        SaleOrderDetail newSupplement = orderDetail(1002L, "3", "2.00", 1);
        newSupplement.setActualNum(new BigDecimal("3"));
        when(saleOrderDetailMapper.selectValidByOrderIdForView(1000L))
                .thenReturn(new ArrayList<>(List.of(nowReturned, newSupplement)));
        AcceptanceItem existing = item(501L, 5L, "5.00", "2.00");
        existing.setSaleOrderDetailId(1001L);
        existing.setActualQuantity(new BigDecimal("4")); // 已录实收
        when(acceptanceItemMapper.selectListByAcceptanceId(5L)).thenReturn(List.of(existing));

        Acceptance result = acceptanceService.syncMissingItems(5L);

        assertEquals(Long.valueOf(5L), result.getId());
        // 新增行：仅补 1002，默认实收=actual_num=3
        ArgumentCaptor<List> insertCaptor = ArgumentCaptor.forClass(List.class);
        verify(acceptanceItemMapper).insertAcceptanceItemBatch(insertCaptor.capture());
        List<AcceptanceItem> inserted = insertCaptor.getValue();
        assertEquals(1, inserted.size());
        assertEquals(Long.valueOf(1002L), inserted.get(0).getSaleOrderDetailId());
        assertEquals(0, new BigDecimal("3").compareTo(inserted.get(0).getActualQuantity()));
        // 已有行被标退货：应送/实收归 0
        ArgumentCaptor<AcceptanceItem> updateCaptor = ArgumentCaptor.forClass(AcceptanceItem.class);
        verify(acceptanceItemMapper).updateAcceptanceItem(updateCaptor.capture());
        assertEquals(Long.valueOf(501L), updateCaptor.getValue().getId());
        assertEquals(0, BigDecimal.ZERO.compareTo(updateCaptor.getValue().getDeliveredQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(updateCaptor.getValue().getActualQuantity()));
    }

    @Test
    void 订单验收_退货被回退后验收行应送实收恢复() {
        // P0 回归：退货回退（order 行已不再标退货）必须把验收行从「应送0/实收0」双向恢复，
        // 否则界面上「退货取消」后实收/差异不变，验收仍会把该行计为 0 元。
        Acceptance draft = orderAcceptance(5L, AcceptanceStatus.DRAFT.getCode());
        when(acceptanceMapper.selectAcceptanceById(5L)).thenReturn(draft);
        SaleOrderDetail restored = orderDetail(1001L, "5", "2.00", 0); // 已回退：change_type=0
        restored.setActualNum(new BigDecimal("5"));
        when(saleOrderDetailMapper.selectValidByOrderIdForView(1000L))
                .thenReturn(new ArrayList<>(List.of(restored)));
        AcceptanceItem stale = item(501L, 5L, "0", "2.00"); // 退货时被归零的验收行
        stale.setSaleOrderDetailId(1001L);
        when(acceptanceItemMapper.selectListByAcceptanceId(5L)).thenReturn(List.of(stale));

        acceptanceService.syncMissingItems(5L);

        ArgumentCaptor<AcceptanceItem> captor = ArgumentCaptor.forClass(AcceptanceItem.class);
        verify(acceptanceItemMapper).updateAcceptanceItem(captor.capture());
        AcceptanceItem update = captor.getValue();
        assertEquals(Long.valueOf(501L), update.getId());
        assertEquals(0, new BigDecimal("5").compareTo(update.getDeliveredQuantity()), "应送恢复=订单应收");
        assertEquals(0, new BigDecimal("5").compareTo(update.getActualQuantity()), "实收默认=应送");
        assertEquals(0, BigDecimal.ZERO.compareTo(update.getDifferenceQuantity()), "差异归零");
        assertEquals(0, new BigDecimal("10.00").compareTo(update.getActualAmount()), "金额=单价×实收");
        assertNull(update.getReasonType(), "差异原因类型清空");
        assertNull(update.getLossReason());
        verify(acceptanceItemMapper, never()).insertAcceptanceItemBatch(anyList());
    }

    @Test
    void 订单验收_非草稿或非订单维度不可同步() {
        Acceptance submitted = orderAcceptance(5L, AcceptanceStatus.SUBMITTED.getCode());
        when(acceptanceMapper.selectAcceptanceById(5L)).thenReturn(submitted);
        assertThrows(ServiceException.class, () -> acceptanceService.syncMissingItems(5L));

        Acceptance legacy = draftAcceptance(); // deliveryOrderId 维度，saleOrderId=null
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(legacy);
        assertThrows(ServiceException.class, () -> acceptanceService.syncMissingItems(1L));
        verify(acceptanceItemMapper, never()).insertAcceptanceItemBatch(anyList());
    }

    @Test
    void 订单一键验收_建单覆盖提交原子完成() {
        when(saleOrderMapper.selectSaleOrderById(1000L)).thenReturn(confirmedOrder());
        when(acceptanceMapper.selectBySaleOrder(1000L)).thenReturn(null);
        when(saleOrderDetailMapper.selectValidByOrderIdForView(1000L))
                .thenReturn(new ArrayList<>(List.of(orderDetail(1001L, "5", "2.00", 0))));
        when(bizCodeService.nextDailyCode(eq("acceptance"), anyString(), eq(3))).thenReturn("YS20260901001");
        when(acceptanceMapper.insertAcceptance(any(Acceptance.class))).thenAnswer(inv -> {
            inv.getArgument(0, Acceptance.class).setId(1L);
            return 1;
        });
        Acceptance draft = orderAcceptance(1L, AcceptanceStatus.DRAFT.getCode());
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(draft);
        AcceptanceItem it = item(501L, 1L, "5.00", "2.00");
        it.setSaleOrderDetailId(1001L);
        it.setActualAmount(new BigDecimal("10.00"));
        when(acceptanceItemMapper.selectListByAcceptanceId(1L)).thenReturn(List.of(it));

        AcceptanceQuickAcceptDTO dto = new AcceptanceQuickAcceptDTO();
        dto.setOrderId(1000L);
        dto.setAcceptDate(LocalDate.of(2026, 9, 1));
        AcceptanceQuickAcceptDTO.Item override = new AcceptanceQuickAcceptDTO.Item();
        override.setSaleOrderDetailId(1001L);
        override.setActualQuantity(new BigDecimal("3")); // 部分短收：原因可不填
        dto.setItems(List.of(override));

        Acceptance result = acceptanceService.quickAccept(dto);

        assertEquals(AcceptanceStatus.SUBMITTED.getCode(), result.getStatus());
        // 覆盖行实收/差异/金额按覆盖值重算
        ArgumentCaptor<AcceptanceItem> updateCaptor = ArgumentCaptor.forClass(AcceptanceItem.class);
        verify(acceptanceItemMapper).updateAcceptanceItem(updateCaptor.capture());
        assertEquals(0, new BigDecimal("3").compareTo(updateCaptor.getValue().getActualQuantity()));
        assertEquals(0, new BigDecimal("-2").compareTo(updateCaptor.getValue().getDifferenceQuantity()));
        assertEquals(0, new BigDecimal("6.00").compareTo(updateCaptor.getValue().getActualAmount()));
        // 提交：镜像回写 + 订单 CONFIRMED→ACCEPTED（不走送货单回写）
        verify(saleOrderDetailMapper, org.mockito.Mockito.atLeastOnce()).updateActualBatch(any(SaleOrderDetail.class));
        verify(saleOrderMapper).updateStatusByIds(List.of(1000L),
                SaleOrderStatus.CONFIRMED.getCode(), SaleOrderStatus.ACCEPTED.getCode());
        verify(saleOrderMapper, never()).updateStatusByDeliveryId(any(), any(), any());
    }

    @Test
    void 订单一键验收_配送日期未到拒绝提交() {
        SaleOrder future = confirmedOrder();
        future.setDeliveryDate(LocalDate.now().plusDays(2));
        when(saleOrderMapper.selectSaleOrderById(1000L)).thenReturn(future);
        when(acceptanceMapper.selectBySaleOrder(1000L)).thenReturn(null);
        when(saleOrderDetailMapper.selectValidByOrderIdForView(1000L))
                .thenReturn(new ArrayList<>(List.of(orderDetail(1001L, "5", "2.00", 0))));
        when(bizCodeService.nextDailyCode(eq("acceptance"), anyString(), eq(3))).thenReturn("YS20260901001");
        when(acceptanceMapper.insertAcceptance(any(Acceptance.class))).thenAnswer(inv -> {
            inv.getArgument(0, Acceptance.class).setId(1L);
            return 1;
        });

        AcceptanceQuickAcceptDTO dto = new AcceptanceQuickAcceptDTO();
        dto.setOrderId(1000L);

        ServiceException ex = assertThrows(ServiceException.class, () -> acceptanceService.quickAccept(dto));
        assertTrue(ex.getMessage().contains("未到"));
        verify(saleOrderMapper, never()).updateStatusByIds(any(), any(), any());
    }

    @Test
    void 订单一键验收_已提交幂等直接返回() {
        when(saleOrderMapper.selectSaleOrderById(1000L)).thenReturn(confirmedOrder());
        Acceptance submitted = orderAcceptance(9L, AcceptanceStatus.SUBMITTED.getCode());
        when(acceptanceMapper.selectBySaleOrder(1000L)).thenReturn(submitted);

        AcceptanceQuickAcceptDTO dto = new AcceptanceQuickAcceptDTO();
        dto.setOrderId(1000L);

        Acceptance result = acceptanceService.quickAccept(dto);

        assertEquals(Long.valueOf(9L), result.getId());
        verify(acceptanceMapper, never()).insertAcceptance(any(Acceptance.class));
        verify(saleOrderDetailMapper, never()).selectValidByOrderIdForView(any());
    }

    @Test
    void 订单一键验收_验收单不属于该订单时拒绝() {
        when(saleOrderMapper.selectSaleOrderById(1000L)).thenReturn(confirmedOrder());
        Acceptance other = orderAcceptance(9L, AcceptanceStatus.DRAFT.getCode());
        other.setSaleOrderId(9999L);
        when(acceptanceMapper.selectAcceptanceById(9L)).thenReturn(other);

        AcceptanceQuickAcceptDTO dto = new AcceptanceQuickAcceptDTO();
        dto.setOrderId(1000L);
        dto.setAcceptanceId(9L);

        ServiceException ex = assertThrows(ServiceException.class, () -> acceptanceService.quickAccept(dto));
        assertTrue(ex.getMessage().contains("不属于该订单"));
    }

    @Test
    void 订单一键验收_超收覆盖未填原因允许并完成提交() {
        when(saleOrderMapper.selectSaleOrderById(1000L)).thenReturn(confirmedOrder());
        when(acceptanceMapper.selectBySaleOrder(1000L)).thenReturn(null);
        when(saleOrderDetailMapper.selectValidByOrderIdForView(1000L))
                .thenReturn(new ArrayList<>(List.of(orderDetail(1001L, "5", "2.00", 0))));
        when(bizCodeService.nextDailyCode(eq("acceptance"), anyString(), eq(3))).thenReturn("YS20260901001");
        when(acceptanceMapper.insertAcceptance(any(Acceptance.class))).thenAnswer(inv -> {
            inv.getArgument(0, Acceptance.class).setId(1L);
            return 1;
        });
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(orderAcceptance(1L, AcceptanceStatus.DRAFT.getCode()));
        AcceptanceItem it = item(501L, 1L, "5.00", "2.00");
        it.setSaleOrderDetailId(1001L);
        it.setActualAmount(new BigDecimal("10.00"));
        when(acceptanceItemMapper.selectListByAcceptanceId(1L)).thenReturn(List.of(it));

        AcceptanceQuickAcceptDTO dto = new AcceptanceQuickAcceptDTO();
        dto.setOrderId(1000L);
        AcceptanceQuickAcceptDTO.Item override = new AcceptanceQuickAcceptDTO.Item();
        override.setSaleOrderDetailId(1001L);
        override.setActualQuantity(new BigDecimal("6")); // 超收未填原因：允许，仅自动记录类型
        dto.setItems(List.of(override));

        Acceptance result = acceptanceService.quickAccept(dto);

        assertEquals(AcceptanceStatus.SUBMITTED.getCode(), result.getStatus());
        ArgumentCaptor<AcceptanceItem> updateCaptor = ArgumentCaptor.forClass(AcceptanceItem.class);
        verify(acceptanceItemMapper).updateAcceptanceItem(updateCaptor.capture());
        assertEquals(2, updateCaptor.getValue().getReasonType(), "超收类型自动记录");
        assertNull(updateCaptor.getValue().getLossReason(), "原因选填，未填则不落库");
        verify(saleOrderMapper).updateStatusByIds(List.of(1000L),
                SaleOrderStatus.CONFIRMED.getCode(), SaleOrderStatus.ACCEPTED.getCode());
    }

    @Test
    void 订单验收撤销_回退订单状态并清镜像() {
        Acceptance submitted = orderAcceptance(1L, AcceptanceStatus.SUBMITTED.getCode());
        when(acceptanceMapper.selectAcceptanceById(1L)).thenReturn(submitted);
        when(monthSettlementMapper.selectByCustomerAndMonth(10L, "2026-09")).thenReturn(null);
        SaleOrder order = confirmedOrder();
        order.setStatus(SaleOrderStatus.ACCEPTED.getCode());
        when(saleOrderMapper.selectSaleOrderByIdIn(List.of(1000L))).thenReturn(List.of(order));
        when(acceptanceItemMapper.selectListByAcceptanceId(1L)).thenReturn(List.of());

        Acceptance result = acceptanceService.revoke(1L, "实收数量复核有误");

        assertEquals(AcceptanceStatus.DRAFT.getCode(), result.getStatus());
        verify(saleOrderMapper).updateStatusByIds(List.of(1000L),
                SaleOrderStatus.ACCEPTED.getCode(), SaleOrderStatus.CONFIRMED.getCode());
        verify(saleOrderDetailMapper).clearActualByOrderId(1000L);
        verify(saleOrderMapper, never()).updateStatusByDeliveryId(any(), any(), any());
    }

    @Test
    void 去验收定位_OA订单维度优先命中() {
        SaleOrder order = new SaleOrder();
        order.setId(1000L);
        order.setCustomerId(10L);
        order.setDeliveryDate(LocalDate.of(2026, 9, 1));
        when(saleOrderMapper.selectSaleOrderById(1000L)).thenReturn(order);
        Acceptance hit = orderAcceptance(88L, AcceptanceStatus.SUBMITTED.getCode());
        when(acceptanceMapper.selectBySaleOrder(1000L)).thenReturn(hit);

        AcceptanceByOrderVO vo = acceptanceService.locateBySaleOrder(1000L);

        assertEquals(Boolean.TRUE, vo.getOrderView());
        assertEquals(Boolean.TRUE, vo.getHasAcceptance());
        assertEquals(Long.valueOf(88L), vo.getAcceptanceId());
        assertEquals(Long.valueOf(10L), vo.getCustomerId());
        assertEquals("2026-09-01", vo.getDeliveryDate());
        verify(acceptanceMapper, never()).selectByCustomerDate(any(), any());
        verify(deliverySourceItemMapper, never()).selectDeliverySourceItemList(any());
    }
}

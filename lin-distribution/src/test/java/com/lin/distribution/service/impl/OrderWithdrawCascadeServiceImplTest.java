package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.PurchaseOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.DeliverySourceItem;
import com.lin.distribution.domain.PurchaseItem;
import com.lin.distribution.domain.PurchaseOrder;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.WithdrawCascadeResultVO;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliverySourceItemMapper;
import com.lin.distribution.mapper.PurchaseItemMapper;
import com.lin.distribution.mapper.PurchaseOrderMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单撤回级联测试（W0-2.1，蓝图「撤回级联/共享单据撤回/空关联单据」）
 *
 * <p>覆盖：已打印/已送达送货单与已入库采购单拒撤；独占待打印单空单作废（原因=订单撤回）；
 * 共享待打印单扣除重算不影响其他订单；采购按汇总键扣除、零行删除、空单作废。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderWithdrawCascadeServiceImplTest {

    @Mock
    private DeliverySourceItemMapper deliverySourceItemMapper;
    @Mock
    private DeliveryOrderMapper deliveryOrderMapper;
    @Mock
    private DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private PurchaseItemMapper purchaseItemMapper;
    @Mock
    private SaleOrderDetailMapper saleOrderDetailMapper;
    @Mock
    private AcceptanceMapper acceptanceMapper;

    @InjectMocks
    private OrderWithdrawCascadeServiceImpl service;

    private static final Long ORDER_ID = 100L;
    private static final String ORDER_CODE = "XD202608280001";
    private static final Long DELIVERY_ID = 10L;
    private static final Long PURCHASE_ID = 20L;

    // ==================== 校验：拒绝线 ====================

    @Test
    void 被已打印送货单占用的订单不可撤回() {
        DeliverySourceItem alloc = sourceItem(DELIVERY_ID, 11L, ORDER_ID, "3");
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID)).thenReturn(List.of(alloc));
        when(deliveryOrderMapper.selectListByIds(List.of(DELIVERY_ID)))
                .thenReturn(List.of(delivery(DELIVERY_ID, "HS202608280001", DeliveryOrderStatus.PRINTED.getCode())));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateOrderWithdrawable(order(ORDER_ID, ORDER_CODE)));

        assertTrue(ex.getMessage().contains("已打印/已送达"));
        assertTrue(ex.getMessage().contains(ORDER_CODE));
    }

    @Test
    void 被已送达送货单占用的订单不可撤回() {
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID))
                .thenReturn(List.of(sourceItem(DELIVERY_ID, 11L, ORDER_ID, "3")));
        when(deliveryOrderMapper.selectListByIds(List.of(DELIVERY_ID)))
                .thenReturn(List.of(delivery(DELIVERY_ID, "HS202608280001", DeliveryOrderStatus.DELIVERED.getCode())));

        assertThrows(ServiceException.class, () -> service.validateOrderWithdrawable(order(ORDER_ID, ORDER_CODE)));
    }

    @Test
    void 已生成验收单的订单不可撤回() {
        // 2026-09-14：撤回后订单回草稿但验收行仍挂在原明细上 → 会出现“已撤回却改不了单”，
        // 正确顺序是先在验收页撤销/删除验收单
        Acceptance acc = new Acceptance();
        acc.setId(9L);
        acc.setCode("YS20260914001");
        acc.setSaleOrderId(ORDER_ID);
        when(acceptanceMapper.selectBySaleOrder(ORDER_ID)).thenReturn(acc);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateOrderWithdrawable(order(ORDER_ID, ORDER_CODE)));

        assertTrue(ex.getMessage().contains("验收单"));
        assertTrue(ex.getMessage().contains("YS20260914001"));
        assertTrue(ex.getMessage().contains(ORDER_CODE));
    }

    @Test
    void 仅有历史关联且无法自动扣除的送货单不可撤回() {
        // 2026-09-14：pre-S14 订单只靠 t_delivery_order_detail.order_id 关联送货单（无 source_item 台账），
        // 级联无法自动扣除/作废 → 放任撤回会让订单与送货单永久不一致
        when(acceptanceMapper.selectBySaleOrder(ORDER_ID)).thenReturn(null);
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
        DeliveryOrderDetail legacy = detail(11L, 15L, new BigDecimal("5"), new BigDecimal("2.00"));
        legacy.setOrderId(ORDER_ID);
        when(deliveryOrderDetailMapper.selectListByOrderIdIn(List.of(ORDER_ID))).thenReturn(List.of(legacy));
        when(deliveryOrderMapper.selectListByIds(List.of(15L)))
                .thenReturn(List.of(delivery(15L, "HS20260827003", DeliveryOrderStatus.DELIVERED.getCode())));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateOrderWithdrawable(order(ORDER_ID, ORDER_CODE)));

        assertTrue(ex.getMessage().contains("历史关联"), ex.getMessage());
        assertTrue(ex.getMessage().contains("HS20260827003"));
    }

    @Test
    void 被已入库采购单引用的订单不可撤回() {
        // 送货侧无占用
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
        PurchaseOrder stocked = purchase(PURCHASE_ID, "PC202608280001", PurchaseOrderStatus.STOCKED.getCode(),
                "[100,101]");
        when(purchaseOrderMapper.selectPurchaseOrderList(any(PurchaseOrder.class))).thenReturn(List.of(stocked));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateOrderWithdrawable(order(ORDER_ID, ORDER_CODE)));

        assertTrue(ex.getMessage().contains("已入库采购单"));
        assertTrue(ex.getMessage().contains(ORDER_CODE));
    }

    @Test
    void 仅待打印送货单与未入库采购单占用时校验通过() {
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID))
                .thenReturn(List.of(sourceItem(DELIVERY_ID, 11L, ORDER_ID, "3")));
        when(deliveryOrderMapper.selectListByIds(List.of(DELIVERY_ID)))
                .thenReturn(List.of(delivery(DELIVERY_ID, "HS202608280001", DeliveryOrderStatus.PENDING.getCode())));
        when(purchaseOrderMapper.selectPurchaseOrderList(any(PurchaseOrder.class)))
                .thenReturn(List.of(purchase(PURCHASE_ID, "PC202608280001", PurchaseOrderStatus.DRAFT.getCode(), "[100]")));

        // 无异常即通过
        service.validateOrderWithdrawable(order(ORDER_ID, ORDER_CODE));

        verify(purchaseOrderMapper, never()).updatePurchaseOrder(any(PurchaseOrder.class));
    }

    @Test
    void 无关联单据时校验直接通过() {
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
        when(purchaseOrderMapper.selectPurchaseOrderList(any(PurchaseOrder.class))).thenReturn(Collections.emptyList());

        service.validateOrderWithdrawable(order(ORDER_ID, ORDER_CODE));

        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
    }

    // ==================== 送货级联 ====================

    @Test
    void 独占待打印送货单扣除后空单自动作废原因订单撤回() {
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID))
                .thenReturn(List.of(sourceItem(DELIVERY_ID, 11L, ORDER_ID, "3")));
        DeliveryOrder delivery = delivery(DELIVERY_ID, "HS202608280001", DeliveryOrderStatus.PENDING.getCode());
        when(deliveryOrderMapper.selectListByIds(List.of(DELIVERY_ID))).thenReturn(List.of(delivery));
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID))
                .thenReturn(List.of(sourceItem(DELIVERY_ID, 11L, ORDER_ID, "3")));

        WithdrawCascadeResultVO result = service.cascadeOnOrderWithdraw(ORDER_ID);

        // 分配释放 + 空单作废 + 原因
        verify(deliverySourceItemMapper).deleteByDeliveryIdAndSaleOrderId(DELIVERY_ID, ORDER_ID);
        verify(deliverySourceItemMapper).deleteByDeliveryId(DELIVERY_ID);
        ArgumentCaptor<DeliveryOrder> captor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderMapper).updateDeliveryOrder(captor.capture());
        assertEquals(DeliveryOrderStatus.VOIDED.getCode(), captor.getValue().getStatus());
        assertEquals("订单撤回", captor.getValue().getVoidReason());
        assertEquals(List.of("HS202608280001"), result.getVoidedDeliveryCodes());
        assertTrue(result.getDeductedDeliveryCodes().isEmpty());
        // 空单整体作废，不再逐行删明细（整单无有效来源，明细节由作废态保护）
        verify(deliveryOrderDetailMapper, never()).deleteDeliveryOrderDetailById(anyLong());
    }

    @Test
    void 共享待打印送货单仅扣除被撤订单并重算不影响其他订单() {
        // 送货单含订单100（被撤，分配2）与订单101（保留，分配3）
        DeliverySourceItem withdrawn = sourceItem(DELIVERY_ID, 11L, ORDER_ID, "2");
        DeliverySourceItem kept = sourceItem(DELIVERY_ID, 11L, 101L, "3");
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID)).thenReturn(List.of(withdrawn));
        when(deliveryOrderMapper.selectListByIds(List.of(DELIVERY_ID)))
                .thenReturn(List.of(delivery(DELIVERY_ID, "HS202608280001", DeliveryOrderStatus.PENDING.getCode())));
        when(deliverySourceItemMapper.selectListByDeliveryId(DELIVERY_ID)).thenReturn(Arrays.asList(withdrawn, kept));

        DeliveryOrderDetail detail = detail(11L, DELIVERY_ID, new BigDecimal("5"), new BigDecimal("2.00"));
        when(deliveryOrderDetailMapper.selectListByDeliveryId(DELIVERY_ID)).thenReturn(List.of(detail));
        when(purchaseOrderMapper.selectPurchaseOrderList(any(PurchaseOrder.class))).thenReturn(Collections.emptyList());

        WithdrawCascadeResultVO result = service.cascadeOnOrderWithdraw(ORDER_ID);

        // 重算：num = 5 - 2 = 3，amount = 2.00 × 3 = 6.00；单据保留不作废
        ArgumentCaptor<DeliveryOrderDetail> captor = ArgumentCaptor.forClass(DeliveryOrderDetail.class);
        verify(deliveryOrderDetailMapper).updateDeliveryOrderDetail(captor.capture());
        assertEquals(0, new BigDecimal("3").compareTo(captor.getValue().getNum()));
        assertEquals(0, new BigDecimal("6.00").compareTo(captor.getValue().getAmount()));
        verify(deliveryOrderDetailMapper, never()).deleteDeliveryOrderDetailById(anyLong());
        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
        assertEquals(List.of("HS202608280001"), result.getDeductedDeliveryCodes());
    }

    @Test
    void 送货单无该订单分配时级联为空操作() {
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
        when(purchaseOrderMapper.selectPurchaseOrderList(any(PurchaseOrder.class))).thenReturn(Collections.emptyList());

        WithdrawCascadeResultVO result = service.cascadeOnOrderWithdraw(ORDER_ID);

        assertTrue(result.getVoidedDeliveryCodes().isEmpty());
        assertTrue(result.getDeductedDeliveryCodes().isEmpty());
        verify(deliverySourceItemMapper, never()).deleteByDeliveryIdAndSaleOrderId(anyLong(), anyLong());
    }

    // ==================== 采购级联 ====================

    @Test
    void 共享采购单按汇总键扣除数量并重算金额与来源() {
        PurchaseOrder purchase = purchase(PURCHASE_ID, "PC202608280001", PurchaseOrderStatus.CONFIRMED.getCode(),
                "[100,101]");
        when(purchaseOrderMapper.selectPurchaseOrderList(any(PurchaseOrder.class))).thenReturn(List.of(purchase));
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
        // 订单100明细：sku1 数量2、sku2 数量3
        when(saleOrderDetailMapper.selectValidByOrderIdIn(List.of(ORDER_ID)))
                .thenReturn(Arrays.asList(
                        saleDetail(1L, 1001L, "白菜", "5kg", new BigDecimal("2")),
                        saleDetail(2L, 1002L, "土豆", "10kg", new BigDecimal("3"))));
        // 采购单明细：sku1 数量5（含订单101的3）、sku2 数量3（全来自订单100）
        PurchaseItem keptItem = purchaseItem(11L, 1001L, "白菜", "5kg", new BigDecimal("5"), new BigDecimal("2.00"));
        PurchaseItem zeroItem = purchaseItem(12L, 1002L, "土豆", "10kg", new BigDecimal("3"), new BigDecimal("1.00"));
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(PURCHASE_ID))
                .thenReturn(Arrays.asList(keptItem, zeroItem));

        WithdrawCascadeResultVO result = service.cascadeOnOrderWithdraw(ORDER_ID);

        // sku1 保留行：5-2=3，小计=3×2.00=6.00
        ArgumentCaptor<PurchaseItem> itemCaptor = ArgumentCaptor.forClass(PurchaseItem.class);
        verify(purchaseItemMapper).updatePurchaseItem(itemCaptor.capture());
        assertEquals(0, new BigDecimal("3").compareTo(itemCaptor.getValue().getQuantity()));
        assertEquals(0, new BigDecimal("6.00").compareTo(itemCaptor.getValue().getSubtotal()));
        // sku2 扣至 0 → 删行
        verify(purchaseItemMapper).deletePurchaseItemByIds(new Long[]{12L});
        // 单据保留：总额重算、来源订单移除 100
        ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).updatePurchaseOrder(poCaptor.capture());
        assertEquals(0, new BigDecimal("6.00").compareTo(poCaptor.getValue().getTotalAmount()));
        assertEquals("[101]", poCaptor.getValue().getSourceOrderIds());
        assertEquals(PurchaseOrderStatus.CONFIRMED.getCode(), poCaptor.getValue().getStatus());
        assertEquals(List.of("PC202608280001"), result.getDeductedPurchaseCodes());
    }

    @Test
    void 采购单扣除后无明细自动作废原因订单撤回() {
        PurchaseOrder purchase = purchase(PURCHASE_ID, "PC202608280001", PurchaseOrderStatus.DRAFT.getCode(), "[100]");
        when(purchaseOrderMapper.selectPurchaseOrderList(any(PurchaseOrder.class))).thenReturn(List.of(purchase));
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
        when(saleOrderDetailMapper.selectValidByOrderIdIn(List.of(ORDER_ID)))
                .thenReturn(List.of(saleDetail(1L, 1001L, "白菜", "5kg", new BigDecimal("2"))));
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(PURCHASE_ID))
                .thenReturn(List.of(purchaseItem(11L, 1001L, "白菜", "5kg", new BigDecimal("2"), new BigDecimal("2.00"))));

        WithdrawCascadeResultVO result = service.cascadeOnOrderWithdraw(ORDER_ID);

        verify(purchaseItemMapper).deletePurchaseItemByIds(new Long[]{11L});
        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).updatePurchaseOrder(captor.capture());
        assertEquals(PurchaseOrderStatus.VOIDED.getCode(), captor.getValue().getStatus());
        assertEquals("订单撤回", captor.getValue().getVoidReason());
        assertEquals(List.of("PC202608280001"), result.getVoidedPurchaseCodes());
    }

    @Test
    void 采购单被手工改小导致超扣时钳到零删行不误伤保留行() {
        PurchaseOrder purchase = purchase(PURCHASE_ID, "PC202608280001", PurchaseOrderStatus.DRAFT.getCode(),
                "[100,101]");
        when(purchaseOrderMapper.selectPurchaseOrderList(any(PurchaseOrder.class))).thenReturn(List.of(purchase));
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
        // 订单100明细 sku1 数量2，但采购行已被手工改小为 1 → 超扣
        when(saleOrderDetailMapper.selectValidByOrderIdIn(List.of(ORDER_ID)))
                .thenReturn(List.of(saleDetail(1L, 1001L, "白菜", "5kg", new BigDecimal("2"))));
        PurchaseItem overDeducted = purchaseItem(11L, 1001L, "白菜", "5kg", new BigDecimal("1"), new BigDecimal("3.00"));
        PurchaseItem otherSku = purchaseItem(12L, 1002L, "土豆", "10kg", new BigDecimal("4"), new BigDecimal("1.00"));
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(PURCHASE_ID))
                .thenReturn(Arrays.asList(overDeducted, otherSku));

        WithdrawCascadeResultVO result = service.cascadeOnOrderWithdraw(ORDER_ID);

        // 超扣行钳到 0 删除；其他 SKU 行不受影响（不 update、不 delete）
        verify(purchaseItemMapper).deletePurchaseItemByIds(new Long[]{11L});
        verify(purchaseItemMapper, never()).updatePurchaseItem(any(PurchaseItem.class));
        // 保留行仍在 → 单据保留，总额=4×1.00=4.00
        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).updatePurchaseOrder(captor.capture());
        assertEquals(0, new BigDecimal("4.00").compareTo(captor.getValue().getTotalAmount()));
        assertEquals(List.of("PC202608280001"), result.getDeductedPurchaseCodes());
    }

    @Test
    void 已作废采购单在级联中跳过() {
        PurchaseOrder voided = purchase(PURCHASE_ID, "PC202608280001", PurchaseOrderStatus.VOIDED.getCode(), "[100]");
        when(purchaseOrderMapper.selectPurchaseOrderList(any(PurchaseOrder.class))).thenReturn(List.of(voided));
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID)).thenReturn(Collections.emptyList());

        WithdrawCascadeResultVO result = service.cascadeOnOrderWithdraw(ORDER_ID);

        verify(purchaseItemMapper, never()).selectPurchaseItemListByPurchaseId(anyLong());
        verify(purchaseOrderMapper, never()).updatePurchaseOrder(any(PurchaseOrder.class));
        assertTrue(result.getVoidedPurchaseCodes().isEmpty());
    }

    @Test
    void 手工采购单不参与级联() {
        // source_type=2 手工单无 source_order_ids，查询自动单时不会被返回
        when(deliverySourceItemMapper.selectValidBySaleOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
        when(purchaseOrderMapper.selectPurchaseOrderList(any(PurchaseOrder.class))).thenReturn(Collections.emptyList());

        WithdrawCascadeResultVO result = service.cascadeOnOrderWithdraw(ORDER_ID);

        assertTrue(result.getVoidedPurchaseCodes().isEmpty());
        assertTrue(result.getDeductedPurchaseCodes().isEmpty());
        verify(saleOrderDetailMapper, never()).selectValidByOrderIdIn(anyList());
    }

    // ==================== 测试数据 ====================

    private SaleOrder order(Long id, String code) {
        SaleOrder order = new SaleOrder();
        order.setId(id);
        order.setCode(code);
        order.setStatus(1);
        order.setDeliveryDate(LocalDate.of(2026, 8, 28));
        return order;
    }

    private DeliverySourceItem sourceItem(Long deliveryId, Long detailId, Long saleOrderId, String qty) {
        DeliverySourceItem item = new DeliverySourceItem();
        item.setDeliveryId(deliveryId);
        item.setDeliveryDetailId(detailId);
        item.setSaleOrderId(saleOrderId);
        item.setAllocatedQuantity(new BigDecimal(qty));
        item.setIsDeleted(false);
        return item;
    }

    private DeliveryOrder delivery(Long id, String code, Integer status) {
        DeliveryOrder delivery = new DeliveryOrder();
        delivery.setId(id);
        delivery.setCode(code);
        delivery.setStatus(status);
        return delivery;
    }

    private DeliveryOrderDetail detail(Long id, Long deliveryId, BigDecimal num, BigDecimal price) {
        DeliveryOrderDetail detail = DeliveryOrderDetail.builder()
                .deliveryId(deliveryId)
                .skuId(1001L)
                .productName("白菜")
                .productUnit("kg")
                .num(num)
                .price(price)
                .isDeleted(false)
                .build();
        detail.setId(id);
        return detail;
    }

    private PurchaseOrder purchase(Long id, String code, Integer status, String sourceOrderIds) {
        PurchaseOrder purchase = new PurchaseOrder();
        purchase.setId(id);
        purchase.setCode(code);
        purchase.setSourceType(1);
        purchase.setSourceOrderIds(sourceOrderIds);
        purchase.setStatus(status);
        return purchase;
    }

    private PurchaseItem purchaseItem(Long id, Long skuId, String name, String spec, BigDecimal qty, BigDecimal price) {
        PurchaseItem item = new PurchaseItem();
        item.setId(id);
        item.setPurchaseId(PURCHASE_ID);
        item.setSkuId(skuId);
        item.setProductName(name);
        item.setProductSpec(spec);
        item.setProductUnit("kg");
        item.setQuantity(qty);
        item.setUnitPrice(price);
        item.setSubtotal(qty.multiply(price));
        return item;
    }

    private SaleOrderDetail saleDetail(Long id, Long skuId, String name, String spec, BigDecimal num) {
        SaleOrderDetail detail = new SaleOrderDetail();
        detail.setId(id);
        detail.setOrderId(ORDER_ID);
        detail.setSkuId(skuId);
        detail.setProductName(name);
        detail.setProductSpec(spec);
        detail.setProductUnit("kg");
        detail.setNum(num);
        detail.setIsDeleted(false);
        return detail;
    }
}

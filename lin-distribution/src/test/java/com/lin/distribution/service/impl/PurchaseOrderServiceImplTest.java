package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.PurchaseOrderStatus;
import com.lin.distribution.domain.PurchaseItem;
import com.lin.distribution.domain.PurchaseModifyLog;
import com.lin.distribution.domain.PurchaseOrder;
import com.lin.distribution.dto.PurchaseBatchDTO;
import com.lin.distribution.mapper.MonthSettlementMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.mapper.PurchaseItemMapper;
import com.lin.distribution.mapper.PurchaseModifyLogMapper;
import com.lin.distribution.mapper.PurchaseOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.vo.PurchaseDaySummaryVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购单重设计测试（D-056~D-063）：日应采汇总 + 分批成本录入 + 已确认纠错 + 批量入库/供应商补录
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceImplTest {

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private PurchaseItemMapper purchaseItemMapper;
    @Mock
    private PurchaseModifyLogMapper purchaseModifyLogMapper;
    @Mock
    private MonthSettlementMapper monthSettlementMapper;
    @Mock
    private ProductSkuMapper productSkuMapper;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private PurchaseOrderServiceImpl purchaseOrderService;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 22);
    private static final Long SKU_1 = 11L;
    private static final Long SKU_2 = 22L;

    // ==================== 日应采汇总（D-056/D-059） ====================

    private PurchaseDaySummaryVO.RequiredRow required(Long skuId, String name, String spec, String unit, String qty) {
        PurchaseDaySummaryVO.RequiredRow row = new PurchaseDaySummaryVO.RequiredRow();
        row.setSkuId(skuId);
        row.setProductName(name);
        row.setProductSpec(spec);
        row.setProductUnit(unit);
        row.setRequiredQty(new BigDecimal(qty));
        return row;
    }

    private PurchaseItem batch(Long id, int batchNo, Long skuId, String name, String spec, String unit,
                               String qty, String price, String subtotal) {
        PurchaseItem item = new PurchaseItem();
        item.setId(id);
        item.setPurchaseId(99L);
        item.setBatchNo(batchNo);
        item.setSkuId(skuId);
        item.setProductName(name);
        item.setProductSpec(spec);
        item.setProductUnit(unit);
        item.setQuantity(new BigDecimal(qty));
        item.setUnitPrice(new BigDecimal(price));
        item.setSubtotal(new BigDecimal(subtotal));
        item.setSort(id == null ? 0 : id.intValue());
        return item;
    }

    @Test
    void 日汇总无采购单时仅返回应采行() {
        when(purchaseItemMapper.selectRequiredSummary(DATE)).thenReturn(Arrays.asList(
                required(SKU_1, "白菜", "", "斤", "5"),
                required(SKU_2, "土豆", "大", "斤", "3")));
        when(purchaseOrderMapper.selectActiveByOrderDate(DATE)).thenReturn(null);

        PurchaseDaySummaryVO vo = purchaseOrderService.daySummary(DATE);

        assertNull(vo.getPurchaseId());
        assertNull(vo.getCode());
        assertEquals(2, vo.getRows().size());
        assertEquals(2, vo.getRequiredItemCount());
        assertEquals(0, vo.getPurchasedItemCount());
        assertEquals(0, new BigDecimal("8").compareTo(vo.getRequiredQty()));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getPurchasedQty()));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getTotalAmount()));
        assertEquals(0, new BigDecimal("5").compareTo(vo.getRows().get(0).getPendingQty()));
        assertEquals(0, vo.getRows().get(0).getBatchCount());
    }

    @Test
    void 日汇总合并应采与已录批次并计算加权均价与超采() {
        PurchaseOrder purchase = purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "14.00");
        when(purchaseItemMapper.selectRequiredSummary(DATE)).thenReturn(Arrays.asList(
                required(SKU_1, "白菜", "", "斤", "5"),
                required(SKU_2, "土豆", "大", "斤", "3")));
        when(purchaseOrderMapper.selectActiveByOrderDate(DATE)).thenReturn(purchase);
        // 白菜两批：2@1.00 + 4@1.50 = 8.00（应采 5 → 超采 1）；土豆一批：3@2.00 = 6.00
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(Arrays.asList(
                batch(1L, 1, SKU_1, "白菜", "", "斤", "2", "1.00", "2.00"),
                batch(2L, 2, SKU_1, "白菜", "", "斤", "4", "1.50", "6.00"),
                batch(3L, 1, SKU_2, "土豆", "大", "斤", "3", "2.00", "6.00")));

        PurchaseDaySummaryVO vo = purchaseOrderService.daySummary(DATE);

        assertEquals(99L, vo.getPurchaseId());
        assertEquals(2, vo.getRows().size());
        PurchaseDaySummaryVO.Row cabbage = vo.getRows().get(0);
        assertEquals(0, new BigDecimal("6").compareTo(cabbage.getPurchasedQty()));
        assertEquals(0, new BigDecimal("-1").compareTo(cabbage.getPendingQty()));
        assertEquals(2, cabbage.getBatchCount());
        assertEquals(0, new BigDecimal("8.00").compareTo(cabbage.getAmount()));
        // 加权均价 = 8.00 / 6 = 1.3333
        assertEquals(0, new BigDecimal("1.3333").compareTo(cabbage.getAvgPrice()));
        assertFalse(cabbage.getOrphan());
        assertEquals(2, cabbage.getBatches().size());
        // 汇总
        assertEquals(1, vo.getOverCount());
        assertEquals(2, vo.getPurchasedItemCount());
        assertEquals(0, new BigDecimal("9").compareTo(vo.getPurchasedQty()));
        assertEquals(0, new BigDecimal("14.00").compareTo(vo.getTotalAmount()));
    }

    @Test
    void 日汇总保留订单已撤回的遗留批次并标orphan() {
        PurchaseOrder purchase = purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "6.00");
        // 应采清单里已无土豆（订单撤回），但批次仍在
        when(purchaseItemMapper.selectRequiredSummary(DATE))
                .thenReturn(Collections.singletonList(required(SKU_1, "白菜", "", "斤", "5")));
        when(purchaseOrderMapper.selectActiveByOrderDate(DATE)).thenReturn(purchase);
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(
                Collections.singletonList(batch(3L, 1, SKU_2, "土豆", "大", "斤", "3", "2.00", "6.00")));

        PurchaseDaySummaryVO vo = purchaseOrderService.daySummary(DATE);

        assertEquals(2, vo.getRows().size());
        PurchaseDaySummaryVO.Row orphan = vo.getRows().get(1);
        assertTrue(orphan.getOrphan());
        assertEquals(0, BigDecimal.ZERO.compareTo(orphan.getRequiredQty()));
        assertEquals(0, new BigDecimal("3").compareTo(orphan.getPurchasedQty()));
        // orphan 行不计入已录品种（否则「未录品种 = 应采 - 已录」会失真）
        assertEquals(0, vo.getPurchasedItemCount());
    }

    @Test
    void 日汇总撤回遗留孤儿与手动新增同键并存时应可继续录入() {
        PurchaseOrder purchase = purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "9.00");
        // 应采清单里已无土豆（订单撤回），遗留批次（is_manual=0）+ 手动新增同商品批次（is_manual=1）并存
        when(purchaseItemMapper.selectRequiredSummary(DATE))
                .thenReturn(Collections.singletonList(required(SKU_1, "白菜", "", "斤", "5")));
        when(purchaseOrderMapper.selectActiveByOrderDate(DATE)).thenReturn(purchase);
        PurchaseItem orphanBatch = batch(3L, 1, SKU_2, "土豆", "大", "斤", "3", "2.00", "6.00");
        orphanBatch.setIsManual(Boolean.FALSE);
        PurchaseItem manualBatch = batch(4L, 2, SKU_2, "土豆", "大", "斤", "2", "1.50", "3.00");
        manualBatch.setIsManual(Boolean.TRUE);
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L))
                .thenReturn(Arrays.asList(orphanBatch, manualBatch));

        PurchaseDaySummaryVO vo = purchaseOrderService.daySummary(DATE);

        assertEquals(2, vo.getRows().size());
        PurchaseDaySummaryVO.Row merged = vo.getRows().get(1);
        // 存在手动批次 → 行可继续录入，不再标孤儿
        assertTrue(merged.getManual());
        assertFalse(merged.getOrphan());
        assertEquals(0, new BigDecimal("5").compareTo(merged.getPurchasedQty()));
        assertEquals(2, merged.getBatchCount());
        // 可录入行计入已录品种
        assertEquals(1, vo.getPurchasedItemCount());
    }

    @Test
    void 日汇总采购日期为空应报错() {
        assertThrows(ServiceException.class, () -> purchaseOrderService.daySummary(null));
    }

    // ==================== 取或创建当日采购单 ====================

    @Test
    void 当日已有采购单则直接返回并带明细() {
        PurchaseOrder exist = purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "0");
        when(purchaseOrderMapper.selectActiveByOrderDate(DATE)).thenReturn(exist);
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L))
                .thenReturn(Collections.singletonList(batch(1L, 1, SKU_1, "白菜", "", "斤", "2", "1.00", "2.00")));

        PurchaseOrder result = purchaseOrderService.getOrCreateDayPurchase(DATE);

        assertEquals(99L, result.getId());
        assertEquals(1, result.getItems().size());
    }

    @Test
    void 当日无采购单则惰性创建草稿() {
        when(purchaseOrderMapper.selectActiveByOrderDate(DATE)).thenReturn(null);
        when(bizCodeService.nextDailyCode("purchase", "PC", 3, DATE)).thenReturn("PC20260822001");
        when(purchaseOrderMapper.insertPurchaseOrder(any(PurchaseOrder.class))).thenAnswer(inv -> {
            ((PurchaseOrder) inv.getArgument(0)).setId(99L);
            return 1;
        });

        PurchaseOrder result = purchaseOrderService.getOrCreateDayPurchase(DATE);

        assertEquals(99L, result.getId());
        assertEquals("PC20260822001", result.getCode());
        assertEquals(DATE, result.getOrderDate());
        assertEquals(PurchaseOrderStatus.DRAFT.getCode(), result.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalAmount()));
    }

    @Test
    void 建单时单号按采购日期而非建单当天() {
        LocalDate businessDate = LocalDate.of(2026, 12, 25);
        when(purchaseOrderMapper.selectActiveByOrderDate(businessDate)).thenReturn(null);
        when(bizCodeService.nextDailyCode("purchase", "PC", 3, businessDate)).thenReturn("PC20261225001");
        when(purchaseOrderMapper.insertPurchaseOrder(any(PurchaseOrder.class))).thenAnswer(inv -> {
            ((PurchaseOrder) inv.getArgument(0)).setId(1L);
            return 1;
        });

        PurchaseOrder result = purchaseOrderService.getOrCreateDayPurchase(businessDate);

        assertEquals("PC20261225001", result.getCode());
        // 强制按业务日期（order_date）取号，而非建单当天
        verify(bizCodeService).nextDailyCode("purchase", "PC", 3, businessDate);
    }

    @Test
    void 并发建单撞唯一键时回查先建者返回() {
        PurchaseOrder winner = purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "0");
        // 第一次回查（预检）无单，insert 撞 uk_purchase_order_active_date，第二次回查命中先建者
        when(purchaseOrderMapper.selectActiveByOrderDate(DATE)).thenReturn(null, winner);
        when(bizCodeService.nextDailyCode("purchase", "PC", 3, DATE)).thenReturn("PC20260822002");
        when(purchaseOrderMapper.insertPurchaseOrder(any(PurchaseOrder.class)))
                .thenThrow(new DuplicateKeyException("uk_purchase_order_active_date"));

        PurchaseOrder result = purchaseOrderService.getOrCreateDayPurchase(DATE);

        assertEquals(99L, result.getId());
        assertNotNull(result.getItems());
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    void 并发建单且回查无单时报冲突() {
        when(purchaseOrderMapper.selectActiveByOrderDate(DATE)).thenReturn(null, (PurchaseOrder) null);
        when(bizCodeService.nextDailyCode("purchase", "PC", 3, DATE)).thenReturn("PC20260822002");
        when(purchaseOrderMapper.insertPurchaseOrder(any(PurchaseOrder.class)))
                .thenThrow(new DuplicateKeyException("uk_purchase_order_active_date"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> purchaseOrderService.getOrCreateDayPurchase(DATE));
        assertTrue(ex.getMessage().contains("冲突"));
    }

    @Test
    void 无批次采购单不能确认() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "0"));
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(Collections.emptyList());

        ServiceException ex = assertThrows(ServiceException.class, () -> purchaseOrderService.confirm(99L));
        assertTrue(ex.getMessage().contains("不能确认"));
    }

    // ==================== 批次录入（D-057/D-058） ====================

    private PurchaseBatchDTO dto(Long skuId, String name, String spec, String unit, String qty, String price) {
        return PurchaseBatchDTO.builder()
                .skuId(skuId).productName(name).productSpec(spec).productUnit(unit)
                .quantity(new BigDecimal(qty)).unitPrice(new BigDecimal(price))
                .build();
    }

    @Test
    void 录入批次不在应采清单时按手动新增落库() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "0"));
        when(purchaseItemMapper.selectRequiredSummary(DATE))
                .thenReturn(Collections.singletonList(required(SKU_1, "白菜", "", "斤", "5")));
        List<PurchaseItem> stored = new ArrayList<>();
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(stored);
        when(purchaseItemMapper.insertPurchaseItem(any(PurchaseItem.class))).thenAnswer(inv -> {
            ((PurchaseItem) inv.getArgument(0)).setId(2L);
            return 1;
        });
        when(purchaseOrderMapper.updatePurchaseOrder(any(PurchaseOrder.class))).thenReturn(1);

        PurchaseItem saved = purchaseOrderService.addBatch(99L, dto(SKU_2, "土豆", "大", "斤", "3", "2.00"));

        assertEquals("土豆", saved.getProductName());
        assertEquals(Boolean.TRUE, saved.getIsManual());
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getRequiredQty()));
        assertEquals(0, new BigDecimal("6.00").compareTo(saved.getSubtotal()));
    }

    @Test
    void 手动新增商品缺品名应拒绝() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "0"));
        when(purchaseItemMapper.selectRequiredSummary(DATE))
                .thenReturn(Collections.singletonList(required(SKU_1, "白菜", "", "斤", "5")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> purchaseOrderService.addBatch(99L, dto(SKU_2, null, null, null, "3", "2.00")));
        assertTrue(ex.getMessage().contains("商品名称不能为空"));
    }

    @Test
    void 临时商品单位缺省为斤() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "0"));
        when(purchaseItemMapper.selectRequiredSummary(DATE))
                .thenReturn(Collections.singletonList(required(SKU_1, "白菜", "", "斤", "5")));
        List<PurchaseItem> stored = new ArrayList<>();
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(stored);
        when(purchaseItemMapper.insertPurchaseItem(any(PurchaseItem.class))).thenAnswer(inv -> {
            ((PurchaseItem) inv.getArgument(0)).setId(3L);
            return 1;
        });
        when(purchaseOrderMapper.updatePurchaseOrder(any(PurchaseOrder.class))).thenReturn(1);

        PurchaseItem saved = purchaseOrderService.addBatch(99L, dto(null, "临时菜", null, null, "1", "1.00"));

        assertNull(saved.getSkuId());
        assertEquals("斤", saved.getProductUnit());
        assertEquals(Boolean.TRUE, saved.getIsManual());
    }

    @Test
    void 录入批次回填快照并重算总额() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "0"));
        when(purchaseItemMapper.selectRequiredSummary(DATE))
                .thenReturn(Collections.singletonList(required(SKU_1, "白菜", "", "斤", "5")));
        // recalcTotal 会二次查询：返回同一个可变列表，doAddBatch 已把新行加入
        List<PurchaseItem> stored = new ArrayList<>();
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(stored);
        when(purchaseItemMapper.insertPurchaseItem(any(PurchaseItem.class))).thenAnswer(inv -> {
            ((PurchaseItem) inv.getArgument(0)).setId(1L);
            return 1;
        });
        when(purchaseOrderMapper.updatePurchaseOrder(any(PurchaseOrder.class))).thenReturn(1);

        PurchaseItem saved = purchaseOrderService.addBatch(99L, dto(SKU_1, "白菜", "", "斤", "5", "1.20"));

        assertEquals(1, saved.getBatchNo());
        assertEquals("白菜", saved.getProductName());
        assertEquals(0, new BigDecimal("5").compareTo(saved.getRequiredQty()));
        assertEquals(0, new BigDecimal("6.00").compareTo(saved.getSubtotal()));
        assertNotNull(saved.getCreateBy());
        assertNotNull(saved.getCreateTime());
        // 总额回写 6.00
        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).updatePurchaseOrder(captor.capture());
        assertEquals(0, new BigDecimal("6.00").compareTo(captor.getValue().getTotalAmount()));
    }

    @Test
    void 同一商品多次录入批次序号递增() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "0"));
        when(purchaseItemMapper.selectRequiredSummary(DATE))
                .thenReturn(Collections.singletonList(required(SKU_1, "白菜", "", "斤", "5")));
        // 已有批次 1
        List<PurchaseItem> stored = new ArrayList<>();
        stored.add(batch(1L, 1, SKU_1, "白菜", "", "斤", "2", "1.00", "2.00"));
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(stored);
        when(purchaseItemMapper.insertPurchaseItem(any(PurchaseItem.class))).thenAnswer(inv -> {
            ((PurchaseItem) inv.getArgument(0)).setId(2L);
            return 1;
        });
        when(purchaseOrderMapper.updatePurchaseOrder(any(PurchaseOrder.class))).thenReturn(1);

        PurchaseItem saved = purchaseOrderService.addBatch(99L, dto(SKU_1, "白菜", "", "斤", "3", "1.50"));

        assertEquals(2, saved.getBatchNo());
    }

    @Test
    void 非草稿状态禁止增删改批次() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.CONFIRMED.getCode(), "0"));

        assertThrows(ServiceException.class,
                () -> purchaseOrderService.addBatch(99L, dto(SKU_1, "白菜", "", "斤", "5", "1.20")));
    }

    @Test
    void 批量录入数量非法整单报错且不落库() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "0"));
        when(purchaseItemMapper.selectRequiredSummary(DATE))
                .thenReturn(Collections.singletonList(required(SKU_1, "白菜", "", "斤", "5")));

        List<PurchaseBatchDTO> list = Arrays.asList(
                dto(SKU_1, "白菜", "", "斤", "2", "1.00"),
                dto(SKU_1, "白菜", "", "斤", "0", "1.00"));

        assertThrows(ServiceException.class, () -> purchaseOrderService.addBatchBulk(99L, list));
        verify(purchaseItemMapper, times(0)).insertPurchaseItem(any(PurchaseItem.class));
    }

    @Test
    void 删除批次后重算总额() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "8.00"));
        PurchaseItem exist = batch(1L, 1, SKU_1, "白菜", "", "斤", "2", "1.00", "2.00");
        when(purchaseItemMapper.selectPurchaseItemById(1L)).thenReturn(exist);
        when(purchaseItemMapper.deletePurchaseItemByIds(any(Long[].class))).thenReturn(1);
        // 删除后仅剩一行 6.00
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(
                Collections.singletonList(batch(2L, 2, SKU_1, "白菜", "", "斤", "4", "1.50", "6.00")));
        when(purchaseOrderMapper.updatePurchaseOrder(any(PurchaseOrder.class))).thenReturn(1);

        purchaseOrderService.deleteBatch(99L, 1L);

        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).updatePurchaseOrder(captor.capture());
        assertEquals(0, new BigDecimal("6.00").compareTo(captor.getValue().getTotalAmount()));
    }

    @Test
    void 修改批次可改数量成本与供应商() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "2.00"));
        when(purchaseItemMapper.selectPurchaseItemById(1L))
                .thenReturn(batch(1L, 1, SKU_1, "白菜", "", "斤", "2", "1.00", "2.00"));
        when(purchaseItemMapper.updateBatchFields(any(PurchaseItem.class))).thenReturn(1);
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L))
                .thenReturn(Collections.singletonList(batch(1L, 1, SKU_1, "白菜", "", "斤", "3", "1.50", "4.50")));
        when(purchaseOrderMapper.updatePurchaseOrder(any(PurchaseOrder.class))).thenReturn(1);

        int rows = purchaseOrderService.updateBatch(99L, 1L, dto(SKU_1, "白菜", "", "斤", "3", "1.50"));

        assertEquals(1, rows);
        ArgumentCaptor<PurchaseItem> captor = ArgumentCaptor.forClass(PurchaseItem.class);
        verify(purchaseItemMapper).updateBatchFields(captor.capture());
        assertEquals(0, new BigDecimal("4.50").compareTo(captor.getValue().getSubtotal()));
        verify(purchaseOrderMapper).updatePurchaseOrder(any(PurchaseOrder.class));
    }

    @Test
    void 修改批次未传供应商时沿用原值() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "2.00"));
        PurchaseItem exist = batch(1L, 1, SKU_1, "白菜", "", "斤", "2", "1.00", "2.00");
        exist.setSupplierId(7L);
        when(purchaseItemMapper.selectPurchaseItemById(1L)).thenReturn(exist);
        when(purchaseItemMapper.updateBatchFields(any(PurchaseItem.class))).thenReturn(1);
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L))
                .thenReturn(Collections.singletonList(batch(1L, 1, SKU_1, "白菜", "", "斤", "3", "1.50", "4.50")));
        when(purchaseOrderMapper.updatePurchaseOrder(any(PurchaseOrder.class))).thenReturn(1);

        // dto 未传 supplierId → 沿用原批次供应商，不被全字段 SET 抹掉
        purchaseOrderService.updateBatch(99L, 1L, dto(SKU_1, "白菜", "", "斤", "3", "1.50"));

        ArgumentCaptor<PurchaseItem> captor = ArgumentCaptor.forClass(PurchaseItem.class);
        verify(purchaseItemMapper).updateBatchFields(captor.capture());
        assertEquals(7L, captor.getValue().getSupplierId());
    }

    @Test
    void 批次不属于该采购单应拒绝() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "0"));
        PurchaseItem other = batch(1L, 1, SKU_1, "白菜", "", "斤", "2", "1.00", "2.00");
        other.setPurchaseId(88L);
        when(purchaseItemMapper.selectPurchaseItemById(1L)).thenReturn(other);

        assertThrows(ServiceException.class, () -> purchaseOrderService.deleteBatch(99L, 1L));
    }

    // ==================== 已确认采购单直接调整（W0-2.5） ====================

    private PurchaseOrder purchase(Long id, String code, Integer status, String total) {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(id);
        po.setCode(code);
        po.setStatus(status);
        po.setOrderDate(DATE);
        po.setTotalAmount(new BigDecimal(total));
        return po;
    }

    @Test
    void 已确认采购单调整数量成本并记录前后金额审计日志() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.CONFIRMED.getCode(), "10.80"));
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(Arrays.asList(
                batch(1L, 1, SKU_1, "白菜", "", "斤", "5", "1.20", "6.00"),
                batch(2L, 1, SKU_2, "土豆", "大", "斤", "3", "1.60", "4.80")));
        when(purchaseOrderMapper.updatePurchaseOrder(any(PurchaseOrder.class))).thenReturn(1);
        when(purchaseItemMapper.updatePurchaseItem(any(PurchaseItem.class))).thenReturn(1);
        when(purchaseModifyLogMapper.insertPurchaseModifyLog(any(PurchaseModifyLog.class))).thenReturn(1);

        PurchaseOrder request = new PurchaseOrder();
        request.setId(99L);
        request.setRemark("单价下调");
        PurchaseItem adjust1 = new PurchaseItem();
        adjust1.setId(1L);
        adjust1.setQuantity(new BigDecimal("4"));
        adjust1.setUnitPrice(new BigDecimal("1.20"));
        PurchaseItem adjust2 = new PurchaseItem();
        adjust2.setId(2L);
        adjust2.setQuantity(new BigDecimal("5"));
        adjust2.setUnitPrice(new BigDecimal("2.00"));
        request.setItems(Arrays.asList(adjust1, adjust2));

        PurchaseOrder result = purchaseOrderService.adjustConfirmedPurchase(request);

        assertEquals(0, new BigDecimal("14.80").compareTo(result.getTotalAmount()));
        ArgumentCaptor<PurchaseItem> itemCaptor = ArgumentCaptor.forClass(PurchaseItem.class);
        verify(purchaseItemMapper, times(2)).updatePurchaseItem(itemCaptor.capture());
        assertEquals(0, new BigDecimal("4.80").compareTo(itemCaptor.getAllValues().get(0).getSubtotal()));
        assertEquals(0, new BigDecimal("10.00").compareTo(itemCaptor.getAllValues().get(1).getSubtotal()));
        ArgumentCaptor<PurchaseModifyLog> logCaptor = ArgumentCaptor.forClass(PurchaseModifyLog.class);
        verify(purchaseModifyLogMapper).insertPurchaseModifyLog(logCaptor.capture());
        PurchaseModifyLog log = logCaptor.getValue();
        assertEquals(0, new BigDecimal("10.80").compareTo(log.getBeforeAmount()));
        assertEquals(0, new BigDecimal("14.80").compareTo(log.getAfterAmount()));
        assertNotNull(log.getBeforeItems());
        assertNotNull(log.getAfterItems());
        assertNotNull(log.getOperator());
        assertEquals("单价下调", log.getRemark());
    }

    @Test
    void 非已确认状态采购单禁止直接调整() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.STOCKED.getCode(), "10.80"));

        PurchaseOrder request = new PurchaseOrder();
        request.setId(99L);
        assertThrows(ServiceException.class, () -> purchaseOrderService.adjustConfirmedPurchase(request));
    }

    @Test
    void 调整明细含非已有行应拒绝() {
        when(purchaseOrderMapper.selectPurchaseOrderById(99L))
                .thenReturn(purchase(99L, "PC20260822001", PurchaseOrderStatus.CONFIRMED.getCode(), "10.80"));
        when(purchaseItemMapper.selectPurchaseItemListByPurchaseId(99L)).thenReturn(
                Collections.singletonList(batch(1L, 1, SKU_1, "白菜", "", "斤", "5", "1.20", "6.00")));

        PurchaseOrder request = new PurchaseOrder();
        request.setId(99L);
        PurchaseItem extra = new PurchaseItem();
        extra.setId(999L);
        extra.setQuantity(new BigDecimal("1"));
        extra.setUnitPrice(new BigDecimal("1.00"));
        request.setItems(Collections.singletonList(extra));

        assertThrows(ServiceException.class, () -> purchaseOrderService.adjustConfirmedPurchase(request));
    }

    @Test
    void 查询采购单调整日志返回列表() {
        when(purchaseModifyLogMapper.selectListByPurchaseId(99L))
                .thenReturn(Collections.singletonList(new PurchaseModifyLog()));
        assertEquals(1, purchaseOrderService.selectModifyLogsByPurchaseId(99L).size());
    }

    // ==================== S2-2.2 批量入库（确认成本）与供应商补录 ====================

    @Test
    void 批量入库应全部置为已入库() {
        when(purchaseOrderMapper.selectPurchaseOrderById(1L))
                .thenReturn(purchase(1L, "PC20260822001", PurchaseOrderStatus.CONFIRMED.getCode(), "10.00"));
        when(purchaseOrderMapper.selectPurchaseOrderById(2L))
                .thenReturn(purchase(2L, "PC20260822002", PurchaseOrderStatus.CONFIRMED.getCode(), "20.00"));
        when(purchaseOrderMapper.updatePurchaseOrder(any(PurchaseOrder.class))).thenReturn(1);

        int rows = purchaseOrderService.batchStockIn(new Long[]{1L, 2L});

        assertEquals(2, rows);
        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper, times(2)).updatePurchaseOrder(captor.capture());
        assertEquals(PurchaseOrderStatus.STOCKED.getCode(), captor.getAllValues().get(0).getStatus());
        assertEquals(PurchaseOrderStatus.STOCKED.getCode(), captor.getAllValues().get(1).getStatus());
    }

    @Test
    void 批量入库含非已确认单应整体报错() {
        when(purchaseOrderMapper.selectPurchaseOrderById(1L))
                .thenReturn(purchase(1L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "10.00"));

        assertThrows(ServiceException.class, () -> purchaseOrderService.batchStockIn(new Long[]{1L, 2L}));
        verify(purchaseOrderMapper, times(0)).updatePurchaseOrder(any(PurchaseOrder.class));
    }

    @Test
    void 批量入库集合为空应报错() {
        assertThrows(ServiceException.class, () -> purchaseOrderService.batchStockIn(new Long[0]));
    }

    @Test
    void 已确认单供应商补录应更新供应商与采购员() {
        when(purchaseOrderMapper.selectPurchaseOrderById(1L))
                .thenReturn(purchase(1L, "PC20260822001", PurchaseOrderStatus.CONFIRMED.getCode(), "10.00"));
        when(purchaseOrderMapper.updatePurchaseOrder(any(PurchaseOrder.class))).thenReturn(1);

        int rows = purchaseOrderService.backfillSupplier(1L, null, "张记蔬菜", "李四");

        assertEquals(1, rows);
        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).updatePurchaseOrder(captor.capture());
        assertEquals("张记蔬菜", captor.getValue().getSupplierName());
        assertEquals("李四", captor.getValue().getPurchaser());
    }

    @Test
    void 已入库单禁止供应商补录() {
        when(purchaseOrderMapper.selectPurchaseOrderById(1L))
                .thenReturn(purchase(1L, "PC20260822001", PurchaseOrderStatus.STOCKED.getCode(), "10.00"));

        assertThrows(ServiceException.class,
                () -> purchaseOrderService.backfillSupplier(1L, null, "张记蔬菜", null));
    }

    @Test
    void 供应商与采购员均为空时补录应报错() {
        when(purchaseOrderMapper.selectPurchaseOrderById(1L))
                .thenReturn(purchase(1L, "PC20260822001", PurchaseOrderStatus.CONFIRMED.getCode(), "10.00"));

        assertThrows(ServiceException.class,
                () -> purchaseOrderService.backfillSupplier(1L, null, "  ", " "));
    }

    // ==================== 单头修改（D-059） ====================

    @Test
    void 修改单头默认供应商采购员与备注() {
        when(purchaseOrderMapper.selectPurchaseOrderById(1L))
                .thenReturn(purchase(1L, "PC20260822001", PurchaseOrderStatus.DRAFT.getCode(), "10.00"));
        when(purchaseOrderMapper.updatePurchaseHeader(any(PurchaseOrder.class))).thenReturn(1);

        PurchaseOrder request = new PurchaseOrder();
        request.setId(1L);
        request.setSupplierName("城北农批");
        request.setPurchaser("张三");
        request.setRemark("默认单头");

        int rows = purchaseOrderService.updatePurchaseHeader(request);

        assertEquals(1, rows);
        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).updatePurchaseHeader(captor.capture());
        assertEquals("城北农批", captor.getValue().getSupplierName());
        assertEquals("张三", captor.getValue().getPurchaser());
        assertEquals("默认单头", captor.getValue().getRemark());
    }
}

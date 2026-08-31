package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.DeliveryGenerateTrigger;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.DeliveryScopeType;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.DeliveryBatch;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.DeliverySourceItem;
import com.lin.distribution.domain.JobRunLog;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.DeliveryByOrdersDTO;
import com.lin.distribution.dto.SaleOrderUpdateStatusDTO;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.DeliveryBatchMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliverySourceItemMapper;
import com.lin.distribution.mapper.JobRunLogMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.DeliveryGenerationService;
import com.lin.distribution.service.SaleOrderService;
import com.lin.distribution.vo.DeliveryGeneratePreviewVO;
import com.lin.distribution.vo.GenerateResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统一生成服务测试（S14/T3，DESIGN.md §5.1）
 *
 * <p>覆盖：幂等跳过、三态分支（正常生成/作废重建 D-022/补充单 D-023）、
 * 组单策略快照（A类总单/B类按点）、五元组合并与不同价拆行（D-024）、
 * 临时商品防误并、批次快照复用（D-016）、source_item 台账落库、
 * generateForDate 多客户隔离与 job_run_log（Q36/D-037）、旧勾选入口兼容语义（D-025）。</p>
 */
@ExtendWith(MockitoExtension.class)
class DeliveryGenerationServiceImplTest {

    @Mock
    private DeliveryOrderMapper deliveryOrderMapper;
    @Mock
    private DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    @Mock
    private DeliverySourceItemMapper deliverySourceItemMapper;
    @Mock
    private DeliveryBatchMapper deliveryBatchMapper;
    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private SaleOrderDetailMapper saleOrderDetailMapper;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private JobRunLogMapper jobRunLogMapper;
    @Mock
    private BizCodeService bizCodeService;
    @Mock
    private SaleOrderService saleOrderService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private DeliveryGenerationServiceImpl generationService;

    @Captor
    private ArgumentCaptor<List<DeliverySourceItem>> sourceListCaptor;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 29);
    private static final Long CUSTOMER = 100L;
    private static final Long POINT_1 = 101L;
    private static final Long POINT_2 = 102L;
    private static final Long SKU_1 = 11L;
    private static final Long SKU_2 = 22L;

    private final AtomicInteger deliverySeq = new AtomicInteger(0);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // TransactionTemplate 直通执行回调（单测不启真事务）
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction((TransactionStatus) null));
        lenient().when(bizCodeService.nextDailyCode("deliveryOrder", "HS", 3))
                .thenReturn("HS20260829001", "HS20260829002", "HS20260829003", "HS20260829004", "HS20260829005");
        // 默认：无既有送货单（V 为空，正常生成分支）
        lenient().when(deliveryOrderMapper.selectActiveByCustomerAndDate(any(), any()))
                .thenReturn(Collections.emptyList());
        // 插入回填主键（逐次递增保证唯一）
        deliverySeq.set(0);
        lenient().when(deliveryOrderMapper.insertDeliveryOrder(any(DeliveryOrder.class))).thenAnswer(inv -> {
            DeliveryOrder order = inv.getArgument(0);
            order.setId((long) deliverySeq.incrementAndGet());
            return 1;
        });
        lenient().when(deliveryOrderDetailMapper.insertDeliveryOrderDetail(any(DeliveryOrderDetail.class))).thenAnswer(inv -> {
            DeliveryOrderDetail detail = inv.getArgument(0);
            detail.setId(100L + detail.getDeliveryId());
            return 1;
        });
    }

    // ==================== 造数工具 ====================

    private SaleOrder order(Long id, String code, Long customerId, Long deptId, LocalDate deliveryDate) {
        SaleOrder o = new SaleOrder();
        o.setId(id);
        o.setCode(code);
        o.setCustomerId(customerId);
        o.setCustomerDeptId(deptId);
        o.setStatus(SaleOrderStatus.CONFIRMED.getCode());
        o.setDeliveryDate(deliveryDate);
        return o;
    }

    private SaleOrderDetail row(Long orderId, Long orderDetailId, Long customerId, Long deptId, Long skuId,
                                String name, String unit, String spec, String price, String num) {
        SaleOrderDetail d = new SaleOrderDetail();
        d.setId(orderDetailId);
        d.setOrderId(orderId);
        d.setOrderCode("XD" + orderId);
        d.setCustomerId(customerId);
        d.setCustomerDeptId(deptId);
        d.setSkuId(skuId);
        d.setProductName(name);
        d.setProductUnit(unit);
        d.setProductSpec(spec);
        d.setProductPrice(new BigDecimal(price));
        d.setNum(new BigDecimal(num));
        return d;
    }

    /** 订单1001：点1 白菜2斤@2 + 白菜3斤@2（同价可并）+ 白菜1斤@2.5（不同价必拆）；订单1002：点2 土豆4斤@3.5 */
    private List<SaleOrderDetail> buildRows(List<Long> orderIds) {
        List<SaleOrderDetail> rows = new ArrayList<>(Arrays.asList(
                row(1001L, 5001L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "2"),
                row(1001L, 5002L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "3"),
                row(1001L, 5003L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.50", "1"),
                row(1002L, 5004L, CUSTOMER, POINT_2, SKU_2, "土豆", "斤", "大", "3.50", "4")));
        rows.removeIf(r -> !orderIds.contains(r.getOrderId()));
        return rows;
    }

    private void stubMissed(SaleOrder... orders) {
        when(saleOrderMapper.selectMissedConfirmedOrders(eq(CUSTOMER), eq(DATE))).thenReturn(Arrays.asList(orders));
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList()))
                .thenAnswer(inv -> buildRows(inv.getArgument(0)));
    }

    private void stubNewBatch(String scopeType, boolean mergeSameItem) {
        when(deliveryBatchMapper.selectByCustomerAndDateForUpdate(eq(CUSTOMER), eq(DATE))).thenReturn(null);
        Customer customer = new Customer();
        customer.setId(CUSTOMER);
        customer.setDocScopeType(scopeType);
        customer.setDocMergeSameItem(mergeSameItem);
        when(customerMapper.selectCustomerById(CUSTOMER)).thenReturn(customer);
        when(deliveryBatchMapper.insertDeliveryBatch(any(DeliveryBatch.class))).thenAnswer(inv -> {
            DeliveryBatch batch = inv.getArgument(0);
            batch.setId(700L);
            return 1;
        });
    }

    // ==================== 幂等 ====================

    @Test
    void 无遗漏订单应幂等跳过且不建批次() {
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE)).thenReturn(Collections.emptyList());

        GenerateResultVO result = generationService.generateForCustomer(CUSTOMER, DATE);

        assertTrue(result.getCreatedOrders().isEmpty());
        assertTrue(result.getSkippedReasons().get(0).contains("无遗漏订单"));
        verify(deliveryBatchMapper, never()).insertDeliveryBatch(any(DeliveryBatch.class));
        verify(deliveryOrderMapper, never()).insertDeliveryOrder(any(DeliveryOrder.class));
    }

    @Test
    void 手工生成参数缺失应报错() {
        assertThrows(ServiceException.class, () -> generationService.generateForCustomer(null, DATE));
        assertThrows(ServiceException.class, () -> generationService.generateForCustomer(CUSTOMER, null));
    }

    // ==================== 正常生成（V 为空） ====================

    @Test
    void B类按点分单且五元组合并不同价拆行() {
        stubMissed(order(1001L, "XD1001", CUSTOMER, POINT_1, DATE), order(1002L, "XD1002", CUSTOMER, POINT_2, DATE));
        stubNewBatch(DeliveryScopeType.DELIVERY_POINT_DATE, true);

        GenerateResultVO result = generationService.generateForCustomer(CUSTOMER, DATE);

        // 两张单：点1、点2
        assertEquals(2, result.getCreatedOrders().size());
        ArgumentCaptor<DeliveryOrder> orderCaptor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderMapper, times(2)).insertDeliveryOrder(orderCaptor.capture());
        List<DeliveryOrder> orders = orderCaptor.getAllValues();
        assertEquals(POINT_1, orders.get(0).getDeliveryPointId());
        assertEquals(POINT_2, orders.get(1).getDeliveryPointId());
        orders.forEach(o -> {
            assertEquals(700L, o.getBatchId());
            assertEquals(DeliveryScopeType.DELIVERY_POINT_DATE, o.getScopeType());
            assertEquals(0, o.getDocKind());
            assertNull(o.getPredecessorId());
            assertEquals(DeliveryOrderStatus.PENDING.getCode(), o.getStatus());
            assertEquals(DATE, o.getDeliveryDate());
        });

        // 点1 明细：白菜@2 合并 5 斤 + 白菜@2.5 独立 1 斤（不同价必拆行 D-024）；点2：土豆 4 斤
        ArgumentCaptor<DeliveryOrderDetail> detailCaptor = ArgumentCaptor.forClass(DeliveryOrderDetail.class);
        verify(deliveryOrderDetailMapper, times(3)).insertDeliveryOrderDetail(detailCaptor.capture());
        List<DeliveryOrderDetail> details = detailCaptor.getAllValues();
        assertEquals(0, new BigDecimal("5").compareTo(details.get(0).getNum()));
        assertEquals(0, new BigDecimal("2.00").compareTo(details.get(0).getPrice()));
        assertEquals(0, new BigDecimal("10.00").compareTo(details.get(0).getAmount()));
        assertEquals(0, new BigDecimal("1").compareTo(details.get(1).getNum()));
        assertEquals(0, new BigDecimal("2.50").compareTo(details.get(1).getPrice()));
        assertEquals("", details.get(0).getOrderCode());
        assertEquals(POINT_1, details.get(0).getCustomerDeptId());
        assertEquals(POINT_2, details.get(2).getCustomerDeptId());

        // source_item 4 条（一订单行一条分配台账），delivery_detail_id 正确回填
        verify(deliverySourceItemMapper, times(3)).batchInsertDeliverySourceItem(sourceListCaptor.capture());
        List<DeliverySourceItem> allSources = sourceListCaptor.getAllValues().stream()
                .flatMap(List::stream).collect(Collectors.toList());
        assertEquals(4, allSources.size());
        DeliverySourceItem first = allSources.get(0);
        assertEquals(5001L, first.getSaleOrderDetailId());
        assertEquals(1001L, first.getSaleOrderId());
        assertEquals(POINT_1, first.getCustomerDeptId());
        assertEquals(details.get(0).getId(), first.getDeliveryDetailId());
        assertEquals(orders.get(0).getId(), first.getDeliveryId());
        assertEquals(0, new BigDecimal("2").compareTo(first.getAllocatedQuantity()));
        assertEquals(0, new BigDecimal("2.00").compareTo(first.getUnitPrice()));
        // 5003（白菜@2.5）归属第二条明细
        assertEquals(details.get(1).getId(), allSources.get(2).getDeliveryDetailId());
    }

    @Test
    void A类总单跨点一张单且明细合并后dept仅留台账() {
        // 订单1001(点1) + 订单1002(点2)，总单策略合并：全客户一张单
        stubMissed(order(1001L, "XD1001", CUSTOMER, POINT_1, DATE), order(1002L, "XD1002", CUSTOMER, POINT_2, DATE));
        stubNewBatch(DeliveryScopeType.CUSTOMER_DATE, true);

        GenerateResultVO result = generationService.generateForCustomer(CUSTOMER, DATE);

        assertEquals(1, result.getCreatedOrders().size());
        ArgumentCaptor<DeliveryOrder> orderCaptor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderMapper).insertDeliveryOrder(orderCaptor.capture());
        DeliveryOrder totalOrder = orderCaptor.getValue();
        assertNull(totalOrder.getDeliveryPointId());
        assertEquals(DeliveryScopeType.CUSTOMER_DATE, totalOrder.getScopeType());

        // 合并：白菜5斤@2、白菜1斤@2.5、土豆4斤@3.5 → 3 行明细；跨点合并行 dept 置空
        ArgumentCaptor<DeliveryOrderDetail> detailCaptor = ArgumentCaptor.forClass(DeliveryOrderDetail.class);
        verify(deliveryOrderDetailMapper, times(3)).insertDeliveryOrderDetail(detailCaptor.capture());
        List<DeliveryOrderDetail> details = detailCaptor.getAllValues();
        assertNull(details.get(0).getCustomerDeptId());

        // source_item 仍带各点归属（dept 维度仅留在台账）
        verify(deliverySourceItemMapper, times(3)).batchInsertDeliverySourceItem(sourceListCaptor.capture());
        List<DeliverySourceItem> allSources = sourceListCaptor.getAllValues().stream()
                .flatMap(List::stream).collect(Collectors.toList());
        assertEquals(4, allSources.size());
        assertEquals(POINT_1, allSources.get(0).getCustomerDeptId());
        assertEquals(POINT_2, allSources.get(3).getCustomerDeptId());
    }

    @Test
    void 合并开关关闭时一订单行一行且带订单号() {
        stubMissed(order(1001L, "XD1001", CUSTOMER, POINT_1, DATE), order(1002L, "XD1002", CUSTOMER, POINT_2, DATE));
        stubNewBatch(DeliveryScopeType.DELIVERY_POINT_DATE, false);

        GenerateResultVO result = generationService.generateForCustomer(CUSTOMER, DATE);

        assertEquals(2, result.getCreatedOrders().size());
        // 不合并：4 行原始订单行 = 4 条明细
        ArgumentCaptor<DeliveryOrderDetail> detailCaptor = ArgumentCaptor.forClass(DeliveryOrderDetail.class);
        verify(deliveryOrderDetailMapper, times(4)).insertDeliveryOrderDetail(detailCaptor.capture());
        DeliveryOrderDetail first = detailCaptor.getAllValues().get(0);
        assertEquals("XD1001", first.getOrderCode());
        assertEquals(POINT_1, first.getCustomerDeptId());
        assertEquals(0, new BigDecimal("2").compareTo(first.getNum()));
    }

    @Test
    void 临时商品同名单价不同规格不应误并() {
        // sku_id 为空的两行临时商品：同名同单位同价但规格不同 → 不得合并
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE)).thenReturn(
                Collections.singletonList(order(1001L, "XD1001", CUSTOMER, POINT_1, DATE)));
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenReturn(Arrays.asList(
                row(1001L, 5001L, CUSTOMER, POINT_1, null, "特价水果", "份", "小份", "9.90", "1"),
                row(1001L, 5002L, CUSTOMER, POINT_1, null, "特价水果", "份", "大份", "9.90", "2")));
        stubNewBatch(DeliveryScopeType.DELIVERY_POINT_DATE, true);

        generationService.generateForCustomer(CUSTOMER, DATE);

        ArgumentCaptor<DeliveryOrderDetail> detailCaptor = ArgumentCaptor.forClass(DeliveryOrderDetail.class);
        verify(deliveryOrderDetailMapper, times(2)).insertDeliveryOrderDetail(detailCaptor.capture());
        assertEquals("小份", detailCaptor.getAllValues().get(0).getProductSpec());
        assertEquals("大份", detailCaptor.getAllValues().get(1).getProductSpec());
    }

    @Test
    void 价格尾零不同视为同价可合并() {
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE)).thenReturn(
                Collections.singletonList(order(1001L, "XD1001", CUSTOMER, POINT_1, DATE)));
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenReturn(Arrays.asList(
                row(1001L, 5001L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.0", "2"),
                row(1001L, 5002L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "3")));
        stubNewBatch(DeliveryScopeType.DELIVERY_POINT_DATE, true);

        generationService.generateForCustomer(CUSTOMER, DATE);

        verify(deliveryOrderDetailMapper, times(1)).insertDeliveryOrderDetail(any(DeliveryOrderDetail.class));
    }

    // ==================== 批次快照（D-016） ====================

    @Test
    void 批次已存在时复用策略快照不读客户当前配置() {
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE)).thenReturn(
                Collections.singletonList(order(1001L, "XD1001", CUSTOMER, POINT_1, DATE)));
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenReturn(Collections.singletonList(
                row(1001L, 5001L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "2")));
        DeliveryBatch existing = DeliveryBatch.builder()
                .id(700L).customerId(CUSTOMER).deliveryDate(DATE)
                .scopeType(DeliveryScopeType.CUSTOMER_DATE).mergeSameItem(false)
                .status(0).version(0).isDeleted(false).build();
        when(deliveryBatchMapper.selectByCustomerAndDateForUpdate(CUSTOMER, DATE)).thenReturn(existing);

        GenerateResultVO result = generationService.generateForCustomer(CUSTOMER, DATE);

        assertEquals(700L, result.getBatchId());
        // A类总单 + 不合并：一张单、1 条订单行 1 条明细
        ArgumentCaptor<DeliveryOrder> orderCaptor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderMapper).insertDeliveryOrder(orderCaptor.capture());
        assertEquals(DeliveryScopeType.CUSTOMER_DATE, orderCaptor.getValue().getScopeType());
        verify(deliveryOrderDetailMapper, times(1)).insertDeliveryOrderDetail(any(DeliveryOrderDetail.class));
        // 批次期内策略不变：不读客户配置、不更新批次
        verify(customerMapper, never()).selectCustomerById(any());
        verify(deliveryBatchMapper, never()).updateDeliveryBatch(any(DeliveryBatch.class));
    }

    // ==================== 作废重建分支（D-022） ====================

    @Test
    void 未打印原单全部作废并按全部已确认订单重建() {
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE)).thenReturn(
                Collections.singletonList(order(1003L, "XD1003", CUSTOMER, POINT_2, DATE)));
        stubNewBatch(DeliveryScopeType.DELIVERY_POINT_DATE, true);

        // 既有 V：两张未打印原单（点1、点2）
        DeliveryOrder old1 = DeliveryOrder.builder().id(901L).customerId(CUSTOMER)
                .deliveryPointId(POINT_1).status(DeliveryOrderStatus.PENDING.getCode()).build();
        DeliveryOrder old2 = DeliveryOrder.builder().id(902L).customerId(CUSTOMER)
                .deliveryPointId(POINT_2).status(DeliveryOrderStatus.PENDING.getCode()).build();
        when(deliveryOrderMapper.selectActiveByCustomerAndDate(CUSTOMER, DATE)).thenReturn(Arrays.asList(old1, old2));

        // 重建覆盖【全部】已确认订单：原单订单 1001/1002 + 遗漏订单 1003
        when(saleOrderMapper.selectConfirmedByCustomerAndDate(CUSTOMER, DATE)).thenReturn(Arrays.asList(
                order(1001L, "XD1001", CUSTOMER, POINT_1, DATE),
                order(1002L, "XD1002", CUSTOMER, POINT_2, DATE),
                order(1003L, "XD1003", CUSTOMER, POINT_2, DATE)));
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            List<SaleOrderDetail> rows = buildRows(ids);
            if (ids.contains(1003L)) {
                rows.add(row(1003L, 5005L, CUSTOMER, POINT_2, SKU_2, "土豆", "斤", "大", "3.50", "4"));
            }
            return rows;
        });

        GenerateResultVO result = generationService.generateForCustomer(CUSTOMER, DATE);

        // 原单作废：VOIDED + 原因/操作人/时间 + 来源分配软删释放
        ArgumentCaptor<DeliveryOrder> voidCaptor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderMapper, times(2)).updateDeliveryOrder(voidCaptor.capture());
        voidCaptor.getAllValues().forEach(o -> {
            assertEquals(DeliveryOrderStatus.VOIDED.getCode(), o.getStatus());
            assertNotNull(o.getVoidReason());
            assertNotNull(o.getVoidTime());
        });
        verify(deliverySourceItemMapper).deleteByDeliveryId(901L);
        verify(deliverySourceItemMapper).deleteByDeliveryId(902L);

        // 新单 doc_kind=0，predecessor 按配送点挂链（点1→901，点2→902）
        assertEquals(2, result.getCreatedOrders().size());
        ArgumentCaptor<DeliveryOrder> orderCaptor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderMapper, times(2)).insertDeliveryOrder(orderCaptor.capture());
        List<DeliveryOrder> created = orderCaptor.getAllValues();
        assertEquals(901L, created.get(0).getPredecessorId());
        assertEquals(902L, created.get(1).getPredecessorId());
        // 参与订单 = 全部已确认订单（3 张，D-022 非遗漏拼接）
        assertEquals(3, result.getMissedOrders().size());
    }

    // ==================== 补充单分支（D-023） ====================

    @Test
    void 含已打印原单时仅遗漏订单生成补充单且原单不动() {
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE)).thenReturn(
                Collections.singletonList(order(1003L, "XD1003", CUSTOMER, POINT_2, DATE)));
        stubNewBatch(DeliveryScopeType.DELIVERY_POINT_DATE, true);
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenReturn(Collections.singletonList(
                row(1003L, 5005L, CUSTOMER, POINT_2, SKU_2, "土豆", "斤", "大", "3.50", "4")));

        // V 含已打印单 → 补充单分支
        DeliveryOrder printed = DeliveryOrder.builder().id(903L).customerId(CUSTOMER)
                .deliveryPointId(POINT_1).status(DeliveryOrderStatus.PRINTED.getCode()).build();
        when(deliveryOrderMapper.selectActiveByCustomerAndDate(CUSTOMER, DATE))
                .thenReturn(Collections.singletonList(printed));

        GenerateResultVO result = generationService.generateForCustomer(CUSTOMER, DATE);

        // 原单不动
        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
        verify(deliverySourceItemMapper, never()).deleteByDeliveryId(any());
        // 补充单 doc_kind=1、无 predecessor、只用遗漏订单建单
        assertEquals(1, result.getCreatedOrders().size());
        ArgumentCaptor<DeliveryOrder> orderCaptor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderMapper).insertDeliveryOrder(orderCaptor.capture());
        DeliveryOrder supplement = orderCaptor.getValue();
        assertEquals(1, supplement.getDocKind());
        assertNull(supplement.getPredecessorId());
        assertEquals(POINT_2, supplement.getDeliveryPointId());
        // 参与订单仅遗漏订单
        assertEquals(1, result.getMissedOrders().size());
        assertEquals(1003L, result.getMissedOrders().get(0).getId());
        verify(saleOrderMapper, never()).selectConfirmedByCustomerAndDate(any(), any());
    }

    // ==================== generateForDate：多客户隔离 + job_run_log ====================

    @Test
    void 按日期生成聚合多客户且单客户失败不影响其他() {
        SaleOrder orderA = order(1001L, "XD1001", 100L, POINT_1, DATE);
        SaleOrder orderB = order(2001L, "XD2001", 200L, POINT_1, DATE);
        when(saleOrderMapper.selectMissedConfirmedOrders(isNull(), eq(DATE))).thenReturn(Arrays.asList(orderA, orderB));
        // 逐客户事务内会重查遗漏订单（并发幂等护栏），需分别 stub
        when(saleOrderMapper.selectMissedConfirmedOrders(eq(100L), eq(DATE))).thenReturn(Collections.singletonList(orderA));
        when(saleOrderMapper.selectMissedConfirmedOrders(eq(200L), eq(DATE))).thenReturn(Collections.singletonList(orderB));
        when(deliveryBatchMapper.selectByCustomerAndDateForUpdate(eq(100L), eq(DATE))).thenReturn(null);
        Customer customerA = new Customer();
        customerA.setId(100L);
        customerA.setDocScopeType(DeliveryScopeType.DELIVERY_POINT_DATE);
        customerA.setDocMergeSameItem(true);
        when(customerMapper.selectCustomerById(100L)).thenReturn(customerA);
        when(deliveryBatchMapper.insertDeliveryBatch(any(DeliveryBatch.class))).thenAnswer(inv -> 1);
        when(deliveryBatchMapper.selectByCustomerAndDateForUpdate(eq(200L), eq(DATE))).thenReturn(null);
        when(customerMapper.selectCustomerById(200L)).thenReturn(null);
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenReturn(Collections.singletonList(
                row(1001L, 5001L, 100L, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "2")));

        GenerateResultVO result = generationService.generateForDate(DATE, DeliveryGenerateTrigger.SCHEDULED);

        // 客户100 成功建单，客户200 失败不阻断
        assertEquals(1, result.getCreatedOrders().size());
        // SCHEDULED 触发写 job_run_log：部分失败 + 遗漏订单数=1
        ArgumentCaptor<JobRunLog> logCaptor = ArgumentCaptor.forClass(JobRunLog.class);
        verify(jobRunLogMapper).insertJobRunLog(logCaptor.capture());
        JobRunLog jobLog = logCaptor.getValue();
        assertEquals(DeliveryGenerationService.JOB_NAME_DELIVERY_GENERATE, jobLog.getJobName());
        assertEquals(DATE, jobLog.getBizDate());
        assertEquals(JobRunLog.STATUS_PARTIAL, jobLog.getStatus());
        assertEquals(1, jobLog.getWarningCount());
        assertNotNull(jobLog.getRunTime());
    }

    @Test
    void 按日期生成无遗漏时记录成功日志() {
        when(saleOrderMapper.selectMissedConfirmedOrders(isNull(), eq(DATE))).thenReturn(Collections.emptyList());

        GenerateResultVO result = generationService.generateForDate(DATE, DeliveryGenerateTrigger.SCHEDULED);

        assertTrue(result.getCreatedOrders().isEmpty());
        ArgumentCaptor<JobRunLog> logCaptor = ArgumentCaptor.forClass(JobRunLog.class);
        verify(jobRunLogMapper).insertJobRunLog(logCaptor.capture());
        assertEquals(JobRunLog.STATUS_SUCCESS, logCaptor.getValue().getStatus());
        assertEquals(0, logCaptor.getValue().getWarningCount());
        verify(deliveryBatchMapper, never()).insertDeliveryBatch(any(DeliveryBatch.class));
    }

    @Test
    void 按日期生成全部失败时记录失败日志() {
        SaleOrder orderA = order(1001L, "XD1001", 100L, POINT_1, DATE);
        when(saleOrderMapper.selectMissedConfirmedOrders(isNull(), eq(DATE))).thenReturn(Collections.singletonList(orderA));
        when(saleOrderMapper.selectMissedConfirmedOrders(eq(100L), eq(DATE))).thenReturn(Collections.singletonList(orderA));
        when(deliveryBatchMapper.selectByCustomerAndDateForUpdate(eq(100L), eq(DATE))).thenReturn(null);
        when(customerMapper.selectCustomerById(100L)).thenReturn(null); // 客户不存在 → 失败

        GenerateResultVO result = generationService.generateForDate(DATE, DeliveryGenerateTrigger.SCHEDULED);

        assertTrue(result.getCreatedOrders().isEmpty());
        ArgumentCaptor<JobRunLog> logCaptor = ArgumentCaptor.forClass(JobRunLog.class);
        verify(jobRunLogMapper).insertJobRunLog(logCaptor.capture());
        assertEquals(JobRunLog.STATUS_FAILED, logCaptor.getValue().getStatus());
        assertEquals(1, logCaptor.getValue().getWarningCount());
    }

    @Test
    void 手工触发不写任务日志() {
        SaleOrder orderA = order(1001L, "XD1001", 100L, POINT_1, DATE);
        when(saleOrderMapper.selectMissedConfirmedOrders(isNull(), eq(DATE))).thenReturn(Collections.singletonList(orderA));
        when(saleOrderMapper.selectMissedConfirmedOrders(eq(100L), eq(DATE))).thenReturn(Collections.singletonList(orderA));
        when(deliveryBatchMapper.selectByCustomerAndDateForUpdate(eq(100L), eq(DATE))).thenReturn(null);
        Customer customerA = new Customer();
        customerA.setId(100L);
        when(customerMapper.selectCustomerById(100L)).thenReturn(customerA);
        when(deliveryBatchMapper.insertDeliveryBatch(any(DeliveryBatch.class))).thenAnswer(inv -> 1);
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenReturn(Collections.singletonList(
                row(1001L, 5001L, 100L, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "2")));

        GenerateResultVO result = generationService.generateForDate(DATE, DeliveryGenerateTrigger.MANUAL);

        assertEquals(1, result.getCreatedOrders().size());
        verify(jobRunLogMapper, never()).insertJobRunLog(any(JobRunLog.class));
    }

    // ==================== generateForOrders（旧勾选入口兼容语义，D-025） ====================

    @Test
    void 勾选订单入口按客户日期分组补齐全部遗漏() {
        SaleOrder selected = order(1001L, "XD1001", CUSTOMER, POINT_1, DATE);
        SaleOrder other = order(1002L, "XD1002", CUSTOMER, POINT_2, DATE.plusDays(1));
        when(saleOrderMapper.selectSaleOrderByIdIn(Arrays.asList(1001L, 1002L))).thenReturn(Arrays.asList(selected, other));

        // 客户+DATE 组：遗漏含 1001/1003（1003 未被勾选也应补齐）
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE)).thenReturn(Arrays.asList(
                order(1001L, "XD1001", CUSTOMER, POINT_1, DATE),
                order(1003L, "XD1003", CUSTOMER, POINT_1, DATE)));
        // 客户+DATE+1 组：无遗漏
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE.plusDays(1))).thenReturn(Collections.emptyList());
        stubNewBatch(DeliveryScopeType.DELIVERY_POINT_DATE, true);
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenReturn(Arrays.asList(
                row(1001L, 5001L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "2"),
                row(1003L, 5002L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "3")));

        GenerateResultVO result = generationService.generateForOrders(
                DeliveryByOrdersDTO.builder().orderIds(Arrays.asList(1001L, 1002L)).build());

        assertEquals(1, result.getCreatedOrders().size());
        assertTrue(result.getSkippedReasons().stream().anyMatch(s -> s.contains(DATE.plusDays(1).toString())));
        // 参与订单含未被勾选的 1003（补齐全部遗漏）
        assertEquals(2, result.getMissedOrders().size());
    }

    @Test
    void 勾选订单不存在应报错() {
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(404L))).thenReturn(Collections.emptyList());

        assertThrows(ServiceException.class, () -> generationService.generateForOrders(
                DeliveryByOrdersDTO.builder().orderIds(Collections.singletonList(404L)).build()));
    }

    @Test
    void 勾选订单未设配送日期且未传日期应报错() {
        SaleOrder noDate = order(1001L, "XD1001", CUSTOMER, POINT_1, null);
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(1001L)))
                .thenReturn(Collections.singletonList(noDate));

        assertThrows(ServiceException.class, () -> generationService.generateForOrders(
                DeliveryByOrdersDTO.builder().orderIds(Collections.singletonList(1001L)).build()));
    }

    // ==================== confirmDrafts（录单页「选订单·生成送货单」抽屉，草稿一步确认并出单） ====================

    @Test
    void 勾选草稿并开启确认应先转已确认再出单并回填单号() {
        SaleOrder draft = order(1001L, "XD1001", CUSTOMER, POINT_1, DATE);
        draft.setStatus(SaleOrderStatus.DRAFT.getCode());
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(1001L)))
                .thenReturn(Collections.singletonList(draft));
        // 确认后统一生成按 客户+日期 补齐全部遗漏（含同日另一张 1003）
        stubMissed(order(1001L, "XD1001", CUSTOMER, POINT_1, DATE),
                order(1003L, "XD1003", CUSTOMER, POINT_1, DATE));
        stubNewBatch(DeliveryScopeType.DELIVERY_POINT_DATE, true);

        GenerateResultVO result = generationService.generateForOrders(DeliveryByOrdersDTO.builder()
                .orderIds(Collections.singletonList(1001L))
                .confirmDrafts(true)
                .build());

        ArgumentCaptor<SaleOrderUpdateStatusDTO> captor = ArgumentCaptor.forClass(SaleOrderUpdateStatusDTO.class);
        verify(saleOrderService).updateSaleOrderStatus(captor.capture());
        assertEquals(Collections.singletonList(1001L), captor.getValue().getOrderIds());
        assertEquals(SaleOrderStatus.CONFIRMED.getCode(), captor.getValue().getStatus());

        assertEquals(Collections.singletonList("XD1001"), result.getConfirmedOrderCodes());
        assertEquals(1, result.getCreatedOrders().size());
    }

    @Test
    void 未开启草稿确认时草稿不被确认且保留旧入口语义() {
        SaleOrder draft = order(1001L, "XD1001", CUSTOMER, POINT_1, DATE);
        draft.setStatus(SaleOrderStatus.DRAFT.getCode());
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(1001L)))
                .thenReturn(Collections.singletonList(draft));
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE)).thenReturn(Collections.emptyList());

        GenerateResultVO result = generationService.generateForOrders(DeliveryByOrdersDTO.builder()
                .orderIds(Collections.singletonList(1001L))
                .build());

        verify(saleOrderService, never()).updateSaleOrderStatus(any());
        assertTrue(result.getCreatedOrders().isEmpty());
        assertTrue(result.getConfirmedOrderCodes().isEmpty());
    }

    @Test
    void 勾选全为已确认时开启确认开关也不调确认逻辑() {
        SaleOrder confirmed = order(1001L, "XD1001", CUSTOMER, POINT_1, DATE);
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(1001L)))
                .thenReturn(Collections.singletonList(confirmed));
        stubMissed(confirmed);
        stubNewBatch(DeliveryScopeType.DELIVERY_POINT_DATE, true);

        GenerateResultVO result = generationService.generateForOrders(DeliveryByOrdersDTO.builder()
                .orderIds(Collections.singletonList(1001L))
                .confirmDrafts(true)
                .build());

        verify(saleOrderService, never()).updateSaleOrderStatus(any());
        assertTrue(result.getConfirmedOrderCodes().isEmpty());
        assertEquals(1, result.getCreatedOrders().size());
    }

    // ==================== 合单排序（蓝图 W0-2.3） ====================

    @Test
    void 同配送点多订单时以行数最多订单为基准并按下单顺序补充() {
        // 订单1001（点1，2 行：白菜/土豆）为基准；订单1002（点1，1 行：萝卜）按顺序补充
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE)).thenReturn(Arrays.asList(
                order(1001L, "XD1001", CUSTOMER, POINT_1, DATE),
                order(1002L, "XD1002", CUSTOMER, POINT_1, DATE)));
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenReturn(Arrays.asList(
                row(1001L, 5001L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "2"),
                row(1001L, 5002L, CUSTOMER, POINT_1, SKU_2, "土豆", "斤", "大", "3.50", "4"),
                row(1002L, 5003L, CUSTOMER, POINT_1, 33L, "萝卜", "斤", "", "1.50", "5")));
        stubNewBatch(DeliveryScopeType.DELIVERY_POINT_DATE, true);

        GenerateResultVO result = generationService.generateForCustomer(CUSTOMER, DATE);

        assertEquals(1, result.getCreatedOrders().size());
        ArgumentCaptor<DeliveryOrderDetail> detailCaptor = ArgumentCaptor.forClass(DeliveryOrderDetail.class);
        verify(deliveryOrderDetailMapper, times(3)).insertDeliveryOrderDetail(detailCaptor.capture());
        List<DeliveryOrderDetail> details = detailCaptor.getAllValues();
        // 基准订单（1001）行序在前：白菜 → 土豆 → 萝卜（1002 补充）
        assertEquals("白菜", details.get(0).getProductName());
        assertEquals("土豆", details.get(1).getProductName());
        assertEquals("萝卜", details.get(2).getProductName());
        // 未合并单来源行保留订单号
        assertEquals("XD1001", details.get(0).getOrderCode());
        assertEquals("XD1002", details.get(2).getOrderCode());
    }

    @Test
    void 基准订单共享商品时合并数量且保持基准行位置() {
        // 订单1001（2 行：白菜/土豆）基准；订单1002 的 白菜@2 与基准共享五元组 → 并入基准行
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE)).thenReturn(Arrays.asList(
                order(1001L, "XD1001", CUSTOMER, POINT_1, DATE),
                order(1002L, "XD1002", CUSTOMER, POINT_1, DATE)));
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenReturn(Arrays.asList(
                row(1001L, 5001L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "2"),
                row(1001L, 5002L, CUSTOMER, POINT_1, SKU_2, "土豆", "斤", "大", "3.50", "4"),
                row(1002L, 5003L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "3")));
        stubNewBatch(DeliveryScopeType.DELIVERY_POINT_DATE, true);

        GenerateResultVO result = generationService.generateForCustomer(CUSTOMER, DATE);

        ArgumentCaptor<DeliveryOrderDetail> detailCaptor = ArgumentCaptor.forClass(DeliveryOrderDetail.class);
        verify(deliveryOrderDetailMapper, times(2)).insertDeliveryOrderDetail(detailCaptor.capture());
        List<DeliveryOrderDetail> details = detailCaptor.getAllValues();
        // 白菜 位于基准首位，数量 2+3=5
        assertEquals("白菜", details.get(0).getProductName());
        assertEquals(0, new BigDecimal("5").compareTo(details.get(0).getNum()));
        assertEquals(0, new BigDecimal("10.00").compareTo(details.get(0).getAmount()));
        // 合并多来源行订单号置空（G1 口径，溯源走 source_item）
        assertEquals("", details.get(0).getOrderCode());
        assertEquals("土豆", details.get(1).getProductName());
        // source_item 台账 3 条（一订单行一条）
        verify(deliverySourceItemMapper, times(2)).batchInsertDeliverySourceItem(sourceListCaptor.capture());
        List<DeliverySourceItem> allSources = sourceListCaptor.getAllValues().stream()
                .flatMap(List::stream).collect(Collectors.toList());
        assertEquals(3, allSources.size());
    }

    // ==================== 生成前预览（D-039 客户维度待生成清单） ====================

    /** 预览展示字段桩：一次性回查订单头（金额/客户名/配送点名）+ 默认无草稿 */
    private void stubPreviewDisplay() {
        lenient().when(saleOrderMapper.selectSaleOrderByIdIn(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            List<SaleOrder> list = new ArrayList<>();
            for (Long id : ids) {
                SaleOrder o = order(id, "XD" + id, CUSTOMER, POINT_1, DATE);
                o.setAmount(new BigDecimal("100.00"));
                o.setCustomerName("鲜食餐饮");
                o.setCustomerDeptName("点-" + id);
                list.add(o);
            }
            return list;
        });
        lenient().when(saleOrderMapper.selectDraftOrdersByDate(any(), any())).thenReturn(Collections.emptyList());
    }

    /** 预览策略桩：未建批次 → 取客户当前配置（D-041 来源标 CUSTOMER_CONFIG） */
    private void stubPreviewCustomerConfig(String scopeType, boolean mergeSameItem) {
        when(deliveryBatchMapper.selectByCustomerAndDate(CUSTOMER, DATE.toString())).thenReturn(null);
        Customer customer = new Customer();
        customer.setId(CUSTOMER);
        customer.setDocScopeType(scopeType);
        customer.setDocMergeSameItem(mergeSameItem);
        when(customerMapper.selectCustomerById(CUSTOMER)).thenReturn(customer);
    }

    @Test
    void 预览按客户维度给出清单且张数与生成同口径() {
        stubPreviewDisplay();
        stubMissed(order(1001L, "XD1001", CUSTOMER, POINT_1, DATE), order(1002L, "XD1002", CUSTOMER, POINT_2, DATE));
        stubPreviewCustomerConfig(DeliveryScopeType.DELIVERY_POINT_DATE, true);

        DeliveryGeneratePreviewVO preview = generationService.previewGenerate(DATE, CUSTOMER);

        assertEquals(1, preview.getCustomerCount().intValue());
        assertEquals(2, preview.getSourceOrderCount().intValue());
        // B类每点一单：点1 + 点2 = 2 张；点1 白菜@2 合并 + 白菜@2.5 拆行 + 点2 土豆 = 3 行
        assertEquals(2, preview.getExpectedDeliveryCount().intValue());
        assertEquals(3, preview.getExpectedDetailCount().intValue());
        assertEquals(0, new BigDecimal("200.00").compareTo(preview.getTotalAmount()));

        DeliveryGeneratePreviewVO.CustomerPreview customer = preview.getCustomers().get(0);
        assertEquals(CUSTOMER, customer.getCustomerId());
        assertEquals("鲜食餐饮", customer.getCustomerName());
        assertEquals("CREATE", customer.getAction());
        assertEquals("正常生成", customer.getActionDesc());
        assertEquals("CUSTOMER_CONFIG", customer.getScopeSource());
        assertEquals(2, customer.getOrders().size());
        // 造数：订单1001 = 3 行（白菜@2 两行 + 白菜@2.5 一行），订单1002 = 1 行
        assertEquals(3, customer.getOrders().get(0).getItemCount().intValue());
        assertEquals(1, customer.getOrders().get(1).getItemCount().intValue());
        assertFalse(customer.getOrders().get(0).getRebuildCovered());
        assertTrue(customer.getExistingOrders().isEmpty());

        // 预览只读：不建批次、不建单、不写台账、不写任务日志
        verify(deliveryBatchMapper, never()).insertDeliveryBatch(any(DeliveryBatch.class));
        verify(deliveryOrderMapper, never()).insertDeliveryOrder(any(DeliveryOrder.class));
        verify(deliverySourceItemMapper, never()).batchInsertDeliverySourceItem(anyList());
        verify(jobRunLogMapper, never()).insertJobRunLog(any(JobRunLog.class));
    }

    @Test
    void 预览无遗漏订单时返回空清单并提示未确认草稿() {
        SaleOrder draft = new SaleOrder();
        draft.setId(9001L);
        draft.setCode("XD9001");
        draft.setStatus(SaleOrderStatus.DRAFT.getCode());
        when(saleOrderMapper.selectMissedConfirmedOrders(isNull(), eq(DATE))).thenReturn(Collections.emptyList());
        when(saleOrderMapper.selectDraftOrdersByDate(isNull(), eq(DATE))).thenReturn(Collections.singletonList(draft));

        DeliveryGeneratePreviewVO preview = generationService.previewGenerate(DATE, null);

        assertTrue(preview.getCustomers().isEmpty());
        assertEquals(0, preview.getCustomerCount().intValue());
        assertEquals(0, preview.getExpectedDeliveryCount().intValue());
        assertEquals(1, preview.getDraftOrderCount().intValue());
        assertEquals("XD9001", preview.getDraftOrderCodes().get(0));
        // 无涉及订单时不发起展示字段回查
        verify(saleOrderMapper, never()).selectSaleOrderByIdIn(anyList());
    }

    @Test
    void 预览对已打印既有单标补充单分支() {
        stubPreviewDisplay();
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE))
                .thenReturn(Collections.singletonList(order(1003L, "XD1003", CUSTOMER, POINT_2, DATE)));
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenReturn(Collections.singletonList(
                row(1003L, 5005L, CUSTOMER, POINT_2, SKU_2, "土豆", "斤", "大", "3.50", "4")));
        stubPreviewCustomerConfig(DeliveryScopeType.DELIVERY_POINT_DATE, true);
        DeliveryOrder printed = DeliveryOrder.builder().id(901L).customerId(CUSTOMER).deliveryPointId(POINT_1)
                .code("HS901").status(DeliveryOrderStatus.PRINTED.getCode()).printCount(2).docKind(0).build();
        when(deliveryOrderMapper.selectActiveByCustomerAndDate(CUSTOMER, DATE))
                .thenReturn(Collections.singletonList(printed));

        DeliveryGeneratePreviewVO preview = generationService.previewGenerate(DATE, CUSTOMER);
        DeliveryGeneratePreviewVO.CustomerPreview customer = preview.getCustomers().get(0);

        assertEquals("SUPPLEMENT", customer.getAction());
        assertEquals("补充单", customer.getActionDesc());
        assertEquals(1, preview.getSupplementCount().intValue());
        assertEquals(0, preview.getRebuildCount().intValue());
        assertEquals(1, customer.getExistingOrders().size());
        assertEquals("HS901", customer.getExistingOrders().get(0).getCode());
        assertEquals("已打印", customer.getExistingOrders().get(0).getStatusDesc());
        // 补充单仅用遗漏订单 → 点2 一张
        assertEquals(1, customer.getExpectedDeliveryCount().intValue());
        verify(saleOrderMapper, never()).selectConfirmedByCustomerAndDate(any(), any());
    }

    @Test
    void 预览对未打印既有单标作废重建并覆盖当日全部已确认订单() {
        stubPreviewDisplay();
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE))
                .thenReturn(Collections.singletonList(order(1003L, "XD1003", CUSTOMER, POINT_2, DATE)));
        stubPreviewCustomerConfig(DeliveryScopeType.DELIVERY_POINT_DATE, true);
        DeliveryOrder pending = DeliveryOrder.builder().id(901L).customerId(CUSTOMER).deliveryPointId(POINT_1)
                .code("HS901").status(DeliveryOrderStatus.PENDING.getCode()).printCount(0).docKind(0).build();
        when(deliveryOrderMapper.selectActiveByCustomerAndDate(CUSTOMER, DATE))
                .thenReturn(Collections.singletonList(pending));
        // 重建覆盖【全部】已确认订单：原单订单 1001/1002 + 遗漏订单 1003
        when(saleOrderMapper.selectConfirmedByCustomerAndDate(CUSTOMER, DATE)).thenReturn(Arrays.asList(
                order(1001L, "XD1001", CUSTOMER, POINT_1, DATE),
                order(1002L, "XD1002", CUSTOMER, POINT_2, DATE),
                order(1003L, "XD1003", CUSTOMER, POINT_2, DATE)));
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            List<SaleOrderDetail> rows = buildRows(ids);
            if (ids.contains(1003L)) {
                rows.add(row(1003L, 5005L, CUSTOMER, POINT_2, SKU_2, "土豆", "斤", "大", "3.50", "4"));
            }
            return rows;
        });

        DeliveryGeneratePreviewVO preview = generationService.previewGenerate(DATE, CUSTOMER);
        DeliveryGeneratePreviewVO.CustomerPreview customer = preview.getCustomers().get(0);

        assertEquals("REBUILD", customer.getAction());
        assertEquals(1, preview.getRebuildCount().intValue());
        assertEquals(3, customer.getOrders().size());
        assertEquals(2, customer.getExpectedDeliveryCount().intValue());
        assertEquals(3, customer.getExpectedDetailCount().intValue());
        assertTrue(customer.getOrders().get(0).getRebuildCovered());
        assertFalse(customer.getOrders().get(2).getRebuildCovered());
        // 预览只推演：不作废原单、不释放来源分配
        verify(deliveryOrderMapper, never()).updateDeliveryOrder(any(DeliveryOrder.class));
        verify(deliverySourceItemMapper, never()).deleteByDeliveryId(any());
    }

    @Test
    void 预览策略来源已建批次时取批次快照不读客户配置() {
        stubPreviewDisplay();
        when(saleOrderMapper.selectMissedConfirmedOrders(CUSTOMER, DATE))
                .thenReturn(Collections.singletonList(order(1001L, "XD1001", CUSTOMER, POINT_1, DATE)));
        when(saleOrderDetailMapper.selectValidByOrderIdIn(anyList())).thenReturn(Arrays.asList(
                row(1001L, 5001L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "2"),
                row(1001L, 5002L, CUSTOMER, POINT_1, SKU_1, "白菜", "斤", "", "2.00", "3")));
        DeliveryBatch existing = DeliveryBatch.builder().id(700L).customerId(CUSTOMER).deliveryDate(DATE)
                .scopeType(DeliveryScopeType.CUSTOMER_DATE).mergeSameItem(false)
                .status(0).version(0).isDeleted(false).build();
        when(deliveryBatchMapper.selectByCustomerAndDate(CUSTOMER, DATE.toString())).thenReturn(existing);

        DeliveryGeneratePreviewVO preview = generationService.previewGenerate(DATE, CUSTOMER);
        DeliveryGeneratePreviewVO.CustomerPreview customer = preview.getCustomers().get(0);

        assertEquals("BATCH_SNAPSHOT", customer.getScopeSource());
        assertEquals(DeliveryScopeType.CUSTOMER_DATE, customer.getScopeType());
        assertEquals("跨点总单", customer.getScopeDesc());
        assertEquals(Boolean.FALSE, customer.getMergeSameItem());
        assertEquals(1, customer.getExpectedDeliveryCount().intValue());
        // 快照不合并：2 行订单行 = 2 行明细
        assertEquals(2, customer.getExpectedDetailCount().intValue());
        verify(customerMapper, never()).selectCustomerById(any());
    }

    @Test
    void 预览参数缺失应报错() {
        assertThrows(ServiceException.class, () -> generationService.previewGenerate(null, CUSTOMER));
    }
}

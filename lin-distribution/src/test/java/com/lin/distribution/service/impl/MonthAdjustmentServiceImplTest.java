package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.MonthAdjustmentStatus;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.MonthAdjustment;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.MonthAdjustmentMapper;
import com.lin.distribution.mapper.MonthSettlementMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下月调整单测试（蓝图 W0-2.7：独立单号、草稿/提交、应收与采购成本分项留痕）
 */
@ExtendWith(MockitoExtension.class)
class MonthAdjustmentServiceImplTest {

    @Mock
    private MonthAdjustmentMapper monthAdjustmentMapper;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private MonthSettlementMapper monthSettlementMapper;
    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private AcceptanceMapper acceptanceMapper;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private MonthAdjustmentServiceImpl monthAdjustmentService;

    private static final Long CUSTOMER = 100L;

    @BeforeEach
    void setUp() {
        lenient().when(bizCodeService.nextDailyCode("monthAdjustment", "TJ", 3))
                .thenReturn("TJ20260829001");
        lenient().when(customerMapper.selectCustomerById(CUSTOMER))
                .thenAnswer(inv -> {
                    Customer c = new Customer();
                    c.setId(CUSTOMER);
                    return c;
                });
        lenient().when(monthAdjustmentMapper.insert(any(MonthAdjustment.class))).thenAnswer(inv -> {
            MonthAdjustment m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        });
        lenient().when(monthAdjustmentMapper.selectById(1L))
                .thenAnswer(inv -> draft(1L));
        lenient().when(monthAdjustmentMapper.selectById(2L))
                .thenAnswer(inv -> submitted(2L));
        lenient().when(monthAdjustmentMapper.deleteById(1L)).thenReturn(1);
    }

    private MonthAdjustment draft(Long id) {
        MonthAdjustment m = new MonthAdjustment();
        m.setId(id);
        m.setCode("TJ2026082900" + id);
        m.setCustomerId(CUSTOMER);
        m.setBillMonth("2026-08");
        m.setStatus(MonthAdjustmentStatus.DRAFT.getCode());
        m.setIsDeleted(0);
        return m;
    }

    private MonthAdjustment submitted(Long id) {
        MonthAdjustment m = draft(id);
        m.setStatus(MonthAdjustmentStatus.SUBMITTED.getCode());
        return m;
    }

    private MonthAdjustment request(Long customerId, String month, String receivable, String cost) {
        MonthAdjustment m = new MonthAdjustment();
        m.setCustomerId(customerId);
        m.setBillMonth(month);
        m.setReceivableAmount(new BigDecimal(receivable));
        m.setPurchaseCostAmount(new BigDecimal(cost));
        return m;
    }

    @Test
    void 新增下月调整单生成单号并默认草稿() {
        MonthAdjustment result = monthAdjustmentService.create(request(CUSTOMER, "2026-09", "120.00", "30.50"));

        assertNotNull(result.getId());
        assertEquals("TJ20260829001", result.getCode());
        assertEquals(MonthAdjustmentStatus.DRAFT.getCode(), result.getStatus());
        assertEquals(0, new BigDecimal("120.00").compareTo(result.getReceivableAmount()));
        assertEquals(0, new BigDecimal("30.50").compareTo(result.getPurchaseCostAmount()));
        ArgumentCaptor<MonthAdjustment> captor = ArgumentCaptor.forClass(MonthAdjustment.class);
        verify(monthAdjustmentMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getIsDeleted().intValue());
        assertNotNull(captor.getValue().getCreateTime());
    }

    @Test
    void 客户不存在应拒绝() {
        when(customerMapper.selectCustomerById(999L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> monthAdjustmentService.create(request(999L, "2026-09", "1", "1")));
    }

    @Test
    void 缺少结算月份应拒绝() {
        assertThrows(ServiceException.class, () -> monthAdjustmentService.create(request(CUSTOMER, null, "1", "1")));
    }

    @Test
    void 应收与成本同时为0应拒绝() {
        assertThrows(ServiceException.class, () -> monthAdjustmentService.create(request(CUSTOMER, "2026-09", "0", "0")));
    }

    @Test
    void 提交后调整单为已提交() {
        int rows = monthAdjustmentService.submit(1L);
        assertEquals(1, rows);
        ArgumentCaptor<MonthAdjustment> captor = ArgumentCaptor.forClass(MonthAdjustment.class);
        verify(monthAdjustmentMapper).update(captor.capture());
        assertEquals(MonthAdjustmentStatus.SUBMITTED.getCode(), captor.getValue().getStatus());
    }

    @Test
    void 已提交状态不可重复提交() {
        assertThrows(ServiceException.class, () -> monthAdjustmentService.submit(2L));
    }

    @Test
    void 已提交状态不可修改() {
        MonthAdjustment update = new MonthAdjustment();
        update.setId(2L);
        update.setBillMonth("2026-10");
        assertThrows(ServiceException.class, () -> monthAdjustmentService.update(update));
    }

    @Test
    void 已提交状态不可删除() {
        assertThrows(ServiceException.class, () -> monthAdjustmentService.delete(2L));
    }

    @Test
    void 草稿可删除() {
        assertEquals(1, monthAdjustmentService.delete(1L));
        verify(monthAdjustmentMapper).deleteById(1L);
    }

    @Test
    void 查询列表委托Mapper() {
        when(monthAdjustmentMapper.selectList(any(MonthAdjustment.class)))
                .thenReturn(Collections.singletonList(draft(1L)));
        assertEquals(1, monthAdjustmentService.selectList(new MonthAdjustment()).size());
    }

    // ==================== 原订单关联摘要（蓝图 §2 月结调整追溯） ====================

    @Test
    void 订单摘要按归月聚合调整单并合计() {
        SaleOrder order = new SaleOrder();
        order.setId(9L);
        order.setCustomerId(CUSTOMER);
        when(saleOrderMapper.selectSaleOrderById(9L)).thenReturn(order);
        when(acceptanceMapper.selectLatestAcceptDateBySaleOrderId(9L))
                .thenReturn(java.time.LocalDate.of(2026, 8, 20));
        MonthAdjustment tj = new MonthAdjustment();
        tj.setCode("TJ20260829001");
        tj.setReceivableAmount(new BigDecimal("50"));
        tj.setPurchaseCostAmount(new BigDecimal("-30"));
        when(monthAdjustmentMapper.selectList(any(MonthAdjustment.class)))
                .thenReturn(Collections.singletonList(tj));

        com.lin.distribution.vo.OrderAdjustmentSummaryVO vo = monthAdjustmentService.selectBySaleOrderId(9L);

        assertEquals("2026-08", vo.getBillMonth());
        assertEquals(1, vo.getAdjustments().size());
        assertEquals(0, vo.getReceivableTotal().compareTo(new BigDecimal("50")));
        assertEquals(0, vo.getCostTotal().compareTo(new BigDecimal("-30")));
        // 查询条件应为「客户+结算月」
        ArgumentCaptor<MonthAdjustment> captor = ArgumentCaptor.forClass(MonthAdjustment.class);
        verify(monthAdjustmentMapper).selectList(captor.capture());
        assertEquals(CUSTOMER, captor.getValue().getCustomerId());
        assertEquals("2026-08", captor.getValue().getBillMonth());
    }

    @Test
    void 未验收归月的订单摘要为空() {
        SaleOrder order = new SaleOrder();
        order.setId(9L);
        order.setCustomerId(CUSTOMER);
        when(saleOrderMapper.selectSaleOrderById(9L)).thenReturn(order);
        when(acceptanceMapper.selectLatestAcceptDateBySaleOrderId(9L)).thenReturn(null);

        com.lin.distribution.vo.OrderAdjustmentSummaryVO vo = monthAdjustmentService.selectBySaleOrderId(9L);

        assertNull(vo.getBillMonth());
        assertEquals(0, vo.getAdjustments().size());
        verify(monthAdjustmentMapper, never()).selectList(any(MonthAdjustment.class));
    }

    @Test
    void 订单不存在时摘要为空() {
        when(saleOrderMapper.selectSaleOrderById(404L)).thenReturn(null);

        com.lin.distribution.vo.OrderAdjustmentSummaryVO vo = monthAdjustmentService.selectBySaleOrderId(404L);

        assertNull(vo.getBillMonth());
        assertEquals(0, vo.getAdjustments().size());
    }
}

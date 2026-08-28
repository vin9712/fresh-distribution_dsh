package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.AcceptanceStatus;
import com.lin.distribution.constant.MonthAdjustmentStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.MonthAdjustment;
import com.lin.distribution.domain.MonthSettlement;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.MonthAdjustmentMapper;
import com.lin.distribution.mapper.MonthSettlementMapper;
import com.lin.distribution.service.AcceptanceService;
import com.lin.distribution.vo.MonthSettlementPreviewVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户月度结算测试（蓝图 W0-3.1：预览/结算/已结判定）
 */
@ExtendWith(MockitoExtension.class)
class MonthSettlementServiceImplTest {

    @Mock
    private MonthSettlementMapper monthSettlementMapper;
    @Mock
    private MonthAdjustmentMapper monthAdjustmentMapper;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private AcceptanceService acceptanceService;

    @InjectMocks
    private MonthSettlementServiceImpl monthSettlementService;

    private static final Long CUSTOMER = 100L;
    private static final String MONTH = "2026-08";

    private MonthSettlement settled(Long id) {
        MonthSettlement s = new MonthSettlement();
        s.setId(id);
        s.setCustomerId(CUSTOMER);
        s.setBillMonth(MONTH);
        s.setStatus(1);
        return s;
    }

    @Test
    void 预览汇总已提交验收与调整单() {
        when(customerMapper.selectCustomerById(CUSTOMER)).thenReturn(customer());
        Acceptance a1 = acceptance(AcceptanceStatus.SUBMITTED.getCode(), "100.00");
        Acceptance a2 = acceptance(AcceptanceStatus.DRAFT.getCode(), "50.00");
        when(acceptanceService.selectAcceptanceList(any(Acceptance.class))).thenReturn(Arrays.asList(a1, a2));
        MonthAdjustment adj1 = adjustment(MonthAdjustmentStatus.SUBMITTED.getCode(), "20.00", "5.00");
        when(monthAdjustmentMapper.selectList(any(MonthAdjustment.class))).thenReturn(Collections.singletonList(adj1));

        MonthSettlementPreviewVO vo = monthSettlementService.preview(CUSTOMER, MONTH);

        assertEquals(2, vo.getAcceptances().size());
        assertEquals(1, vo.getAdjustments().size());
        // 已提交验收 100 + 调整净额 (20-5)=15 → 应结 115
        assertEquals(0, new BigDecimal("100.00").compareTo(vo.getAcceptedAmount()));
        assertEquals(0, new BigDecimal("15.00").compareTo(vo.getAdjustmentNet()));
        assertEquals(0, new BigDecimal("115.00").compareTo(vo.getSettleAmount()));
    }

    @Test
    void 结算未结客户插入已结记录() {
        when(monthSettlementMapper.selectByCustomerAndMonth(CUSTOMER, MONTH)).thenReturn(null);
        when(monthSettlementMapper.insert(any(MonthSettlement.class))).thenAnswer(inv -> {
            ((MonthSettlement) inv.getArgument(0)).setId(1L);
            return 1;
        });

        MonthSettlement result = monthSettlementService.settle(CUSTOMER, MONTH, "核对无误");

        assertNotNull(result.getId());
        assertEquals(1, result.getStatus());
        assertEquals("2026-08", result.getBillMonth());
        assertNotNull(result.getSettledTime());
        verify(monthSettlementMapper).insert(any(MonthSettlement.class));
    }

    @Test
    void 已月结客户重复结算拒绝() {
        when(monthSettlementMapper.selectByCustomerAndMonth(CUSTOMER, MONTH)).thenReturn(settled(1L));
        assertThrows(ServiceException.class, () -> monthSettlementService.settle(CUSTOMER, MONTH, "重复"));
    }

    @Test
    void 已结判定() {
        when(monthSettlementMapper.selectByCustomerAndMonth(CUSTOMER, MONTH)).thenReturn(settled(1L));
        assertTrue(monthSettlementService.isSettled(CUSTOMER, MONTH));
        when(monthSettlementMapper.selectByCustomerAndMonth(CUSTOMER, "2026-09")).thenReturn(null);
        assertFalse(monthSettlementService.isSettled(CUSTOMER, "2026-09"));
    }

    @Test
    void 列表与查询委托Mapper() {
        when(monthSettlementMapper.selectList(any(MonthSettlement.class)))
                .thenReturn(Collections.singletonList(settled(1L)));
        assertEquals(1, monthSettlementService.selectList(new MonthSettlement()).size());
        when(monthSettlementMapper.selectByCustomerAndMonth(CUSTOMER, MONTH)).thenReturn(settled(1L));
        assertNotNull(monthSettlementService.getByCustomerAndMonth(CUSTOMER, MONTH));
    }

    @Test
    void 非法月份格式拒绝() {
        assertThrows(ServiceException.class, () -> monthSettlementService.settle(CUSTOMER, "2026/08", null));
    }

    private Customer customer() {
        Customer c = new Customer();
        c.setId(CUSTOMER);
        c.setName("城北超市");
        return c;
    }

    private Acceptance acceptance(Integer status, String amount) {
        Acceptance a = new Acceptance();
        a.setCustomerId(CUSTOMER);
        a.setAcceptDate(LocalDate.of(2026, 8, 15));
        a.setStatus(status);
        a.setTotalAmount(new BigDecimal(amount));
        return a;
    }

    private MonthAdjustment adjustment(Integer status, String receivable, String cost) {
        MonthAdjustment m = new MonthAdjustment();
        m.setCustomerId(CUSTOMER);
        m.setBillMonth(MONTH);
        m.setStatus(status);
        m.setReceivableAmount(new BigDecimal(receivable));
        m.setPurchaseCostAmount(new BigDecimal(cost));
        return m;
    }
}

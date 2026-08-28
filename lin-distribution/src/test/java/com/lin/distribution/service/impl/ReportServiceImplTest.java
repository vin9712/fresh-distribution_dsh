package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.dto.ReportVO;
import com.lin.distribution.mapper.ReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 经营概览测试（蓝图 W0-3.3：已/未月结区分、待确认成本不计毛利、周期估算毛利）
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportMapper reportMapper;

    @InjectMocks
    private ReportServiceImpl reportService;

    private static final LocalDate BEGIN = LocalDate.of(2026, 8, 1);
    private static final LocalDate END = LocalDate.of(2026, 8, 31);

    private ReportVO.OverviewSettleAmount bucket(int settled, String amount) {
        ReportVO.OverviewSettleAmount b = new ReportVO.OverviewSettleAmount();
        b.setSettled(settled);
        b.setAmount(new BigDecimal(amount));
        return b;
    }

    @Test
    void 经营概览区分已未月结并计估算毛利() {
        when(reportMapper.selectOverviewAccepted(BEGIN, END)).thenReturn(Arrays.asList(
                bucket(1, "100.00"), bucket(0, "80.00")));
        when(reportMapper.selectOverviewPurchase(BEGIN, END)).thenReturn(new BigDecimal("70.00"));
        when(reportMapper.selectOverviewPendingCost(BEGIN, END)).thenReturn(BigDecimal.ZERO);

        ReportVO.OperatingOverview vo = reportService.overview(BEGIN, END);

        assertEquals(0, new BigDecimal("180.00").compareTo(vo.getAcceptedAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(vo.getSettledAmount()));
        assertEquals(0, new BigDecimal("80.00").compareTo(vo.getUnsettledAmount()));
        assertEquals(0, new BigDecimal("70.00").compareTo(vo.getPurchaseAmount()));
        assertFalse(vo.isHasPendingCost());
        // 估算毛利 = 180 - 70 = 110
        assertEquals(0, new BigDecimal("110.00").compareTo(vo.getGrossProfit()));
    }

    @Test
    void 存在待确认成本时不计算毛利() {
        when(reportMapper.selectOverviewAccepted(BEGIN, END)).thenReturn(Arrays.asList(bucket(0, "50.00")));
        when(reportMapper.selectOverviewPurchase(BEGIN, END)).thenReturn(new BigDecimal("30.00"));
        when(reportMapper.selectOverviewPendingCost(BEGIN, END)).thenReturn(new BigDecimal("10.00"));

        ReportVO.OperatingOverview vo = reportService.overview(BEGIN, END);

        assertTrue(vo.isHasPendingCost());
        assertNull(vo.getGrossProfit());
    }

    @Test
    void 日期范围校验() {
        assertThrows(ServiceException.class, () -> reportService.overview(null, END));
        assertThrows(ServiceException.class, () -> reportService.overview(BEGIN, BEGIN.minusDays(1)));
    }
}

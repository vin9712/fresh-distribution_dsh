package com.lin.distribution.service.impl;

import com.lin.distribution.domain.ReportRow;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 报表服务测试（DESIGN.md 验收标准 12：销售日报按日+客户分组、客户对账单口径）
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportMapper reportMapper;

    @InjectMocks
    private ReportServiceImpl reportService;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 17);
    private static final Long CUSTOMER_A = 100L;
    private static final Long CUSTOMER_B = 200L;
    private static final Long POINT_A1 = 101L;

    private ReportRow row(Long customerId, Long pointId, Long acceptanceId, String acceptanceCode,
                          LocalDate acceptDate, String deliveryCode, String total,
                          String name, String spec, String unit, String actual, String loss, String price, String amount) {
        ReportRow row = new ReportRow();
        row.setCustomerId(customerId);
        row.setCustomerName("客户" + customerId);
        row.setDeliveryPointId(pointId);
        row.setDeliveryPointName("配送点" + pointId);
        row.setAcceptanceId(acceptanceId);
        row.setAcceptanceCode(acceptanceCode);
        row.setAcceptDate(acceptDate);
        row.setDeliveryCode(deliveryCode);
        row.setAcceptanceTotalAmount(new BigDecimal(total));
        row.setProductName(name);
        row.setProductSpec(spec);
        row.setProductUnit(unit);
        row.setActualQuantity(new BigDecimal(actual));
        row.setLossQuantity(new BigDecimal(loss));
        row.setUnitPrice(new BigDecimal(price));
        row.setActualAmount(new BigDecimal(amount));
        return row;
    }

    @Test
    void 日报按客户配送点分组且金额损耗汇总正确() {
        when(reportMapper.selectDailySaleRows(DATE)).thenReturn(Arrays.asList(
                row(CUSTOMER_A, POINT_A1, 1L, "YS1", DATE, "HS1", "16.00", "白菜", "", "斤", "5.00", "1.00", "2.00", "10.00"),
                row(CUSTOMER_A, POINT_A1, 1L, "YS1", DATE, "HS1", "16.00", "土豆", "大", "斤", "3.00", "-1.00", "2.00", "6.00"),
                row(CUSTOMER_B, 201L, 2L, "YS2", DATE, "HS2", "10.00", "白菜", "", "斤", "5.00", "0.00", "2.00", "10.00")
        ));

        List<ReportVO.DailySaleGroup> groups = reportService.dailySale(DATE);

        assertEquals(2, groups.size());
        ReportVO.DailySaleGroup first = groups.get(0);
        assertEquals(CUSTOMER_A, first.getCustomerId());
        assertEquals(POINT_A1, first.getDeliveryPointId());
        assertEquals(0, new BigDecimal("16.00").compareTo(first.getTotalActualAmount()));
        assertEquals(0, new BigDecimal("0.00").compareTo(first.getTotalLossQuantity()));
        // 损耗金额：1*2 + (-1)*2 = 0
        assertEquals(0, new BigDecimal("0.00").compareTo(first.getTotalLossAmount()));
        assertEquals(2, first.getItems().size());
        assertEquals(0, new BigDecimal("10.00").compareTo(first.getItems().get(0).getActualAmount()));
    }

    @Test
    void 日报日期为空应报错() {
        assertThrows(com.lin.common.exception.ServiceException.class, () -> reportService.dailySale(null));
    }

    @Test
    void 对账单按验收单分组且期间合计正确() {
        when(reportMapper.selectStatementRows(eq(CUSTOMER_A), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Arrays.asList(
                        row(CUSTOMER_A, POINT_A1, 1L, "YS1", DATE.minusDays(1), "HS1", "16.00", "白菜", "", "斤", "5.00", "1.00", "2.00", "10.00"),
                        row(CUSTOMER_A, POINT_A1, 1L, "YS1", DATE.minusDays(1), "HS1", "16.00", "土豆", "大", "斤", "3.00", "-1.00", "2.00", "6.00"),
                        row(CUSTOMER_A, POINT_A1, 2L, "YS2", DATE, "HS2", "9.00", "白菜", "", "斤", "4.50", "0.00", "2.00", "9.00")
                ));

        ReportVO.CustomerStatement statement = reportService.customerStatement(CUSTOMER_A, DATE.minusDays(30), DATE);

        assertEquals(2, statement.getAcceptances().size());
        assertEquals(0, new BigDecimal("25.00").compareTo(statement.getTotalAmount()));
        assertEquals(2, statement.getAcceptances().get(0).getItems().size());
        assertEquals("YS1", statement.getAcceptances().get(0).getCode());
        assertEquals(1, statement.getAcceptances().get(1).getItems().size());
    }

    @Test
    void 对账单日期区间颠倒应报错() {
        assertThrows(com.lin.common.exception.ServiceException.class,
                () -> reportService.customerStatement(CUSTOMER_A, DATE, DATE.minusDays(1)));
    }

    @Test
    void 对账单无数据返回空结构() {
        when(reportMapper.selectStatementRows(eq(CUSTOMER_A), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        ReportVO.CustomerStatement statement = reportService.customerStatement(CUSTOMER_A, DATE.minusDays(30), DATE);
        assertEquals(0, statement.getAcceptances().size());
        assertEquals(0, new BigDecimal("0.00").compareTo(statement.getTotalAmount()));
    }
}

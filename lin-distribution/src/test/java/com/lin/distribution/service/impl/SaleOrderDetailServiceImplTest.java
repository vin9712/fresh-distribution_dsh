package com.lin.distribution.service.impl;

import com.lin.distribution.mapper.SaleOrderDetailMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 常用商品统计服务单测（录单页"常用"面板）
 * 规则：customerId 为空返回空列表；days/limit 缺省 30/20 且封顶 90/50；
 *       统计起始时间 = 今天往前 days 天的零点。
 */
@ExtendWith(MockitoExtension.class)
class SaleOrderDetailServiceImplTest {

    @Mock
    private SaleOrderDetailMapper saleOrderDetailMapper;

    @InjectMocks
    private SaleOrderDetailServiceImpl saleOrderDetailService;

    private static final Long CUSTOMER_ID = 10L;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(saleOrderDetailMapper.selectFrequentSkuList(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.Collections.emptyList());
    }

    @Test
    void 客户为空时不查库直接返回空() {
        assertTrue(saleOrderDetailService.selectFrequentSkuList(null, 30, 20).isEmpty());
        verify(saleOrderDetailMapper, never()).selectFrequentSkuList(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void 默认参数为30天20条且起始时间为零点() {
        saleOrderDetailService.selectFrequentSkuList(CUSTOMER_ID, null, null);

        ArgumentCaptor<Long> idCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<LocalDateTime> timeCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Integer> limitCap = ArgumentCaptor.forClass(Integer.class);
        verify(saleOrderDetailMapper).selectFrequentSkuList(idCap.capture(), timeCap.capture(), limitCap.capture());

        assertEquals(CUSTOMER_ID, idCap.getValue());
        assertEquals(20, limitCap.getValue());
        assertEquals(LocalDate.now().minusDays(30).atStartOfDay(), timeCap.getValue());
    }

    @Test
    void 自定义天数与条数透传() {
        saleOrderDetailService.selectFrequentSkuList(CUSTOMER_ID, 7, 5);

        ArgumentCaptor<LocalDateTime> timeCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Integer> limitCap = ArgumentCaptor.forClass(Integer.class);
        verify(saleOrderDetailMapper).selectFrequentSkuList(
                org.mockito.ArgumentMatchers.eq(CUSTOMER_ID), timeCap.capture(), limitCap.capture());

        assertEquals(5, limitCap.getValue());
        assertEquals(LocalDate.now().minusDays(7).atStartOfDay(), timeCap.getValue());
    }

    @Test
    void 超出上限时封顶90天50条() {
        saleOrderDetailService.selectFrequentSkuList(CUSTOMER_ID, 365, 999);

        ArgumentCaptor<LocalDateTime> timeCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Integer> limitCap = ArgumentCaptor.forClass(Integer.class);
        verify(saleOrderDetailMapper).selectFrequentSkuList(
                org.mockito.ArgumentMatchers.eq(CUSTOMER_ID), timeCap.capture(), limitCap.capture());

        assertEquals(50, limitCap.getValue());
        assertEquals(LocalDate.now().minusDays(90).atStartOfDay(), timeCap.getValue());
    }
}

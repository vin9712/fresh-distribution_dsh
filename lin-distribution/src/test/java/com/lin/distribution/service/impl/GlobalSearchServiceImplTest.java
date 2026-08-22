package com.lin.distribution.service.impl;

import com.lin.distribution.dto.GlobalSearchVO;
import com.lin.distribution.mapper.GlobalSearchMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 全局搜索服务单测
 * 规则：keyword 为空/空白返回空分组且不查库；limit 缺省 10、封顶 30；
 *      三组结果分别来自三个 mapper 方法。
 */
@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceImplTest {

    @Mock
    private GlobalSearchMapper globalSearchMapper;

    @InjectMocks
    private GlobalSearchServiceImpl globalSearchService;

    @Test
    void 关键词为空时不查库返回空分组() {
        GlobalSearchVO vo = globalSearchService.search("", 10);
        assertTrue(vo.getCustomers().isEmpty());
        assertTrue(vo.getProducts().isEmpty());
        assertTrue(vo.getOrders().isEmpty());
        verify(globalSearchMapper, never()).selectCustomerHits(any(), anyInt());
        verify(globalSearchMapper, never()).selectProductHits(any(), anyInt());
        verify(globalSearchMapper, never()).selectOrderHits(any(), anyInt());
    }

    @Test
    void 空白关键词同样返回空分组() {
        GlobalSearchVO vo = globalSearchService.search("   ", 10);
        assertTrue(vo.getCustomers().isEmpty());
        assertTrue(vo.getProducts().isEmpty());
        assertTrue(vo.getOrders().isEmpty());
    }

    @Test
    void 默认条数为10() {
        when(globalSearchMapper.selectCustomerHits(eq("白菜"), anyInt())).thenReturn(Collections.emptyList());
        when(globalSearchMapper.selectProductHits(eq("白菜"), anyInt())).thenReturn(Collections.emptyList());
        when(globalSearchMapper.selectOrderHits(eq("白菜"), anyInt())).thenReturn(Collections.emptyList());

        GlobalSearchVO vo = globalSearchService.search("白菜", null);
        verify(globalSearchMapper).selectCustomerHits("白菜", 10);
        verify(globalSearchMapper).selectProductHits("白菜", 10);
        verify(globalSearchMapper).selectOrderHits("白菜", 10);
        assertEquals("白菜", vo.getKeyword());
    }

    @Test
    void 超出上限时封顶30() {
        when(globalSearchMapper.selectCustomerHits(eq("土豆"), anyInt())).thenReturn(Collections.emptyList());
        when(globalSearchMapper.selectProductHits(eq("土豆"), anyInt())).thenReturn(Collections.emptyList());
        when(globalSearchMapper.selectOrderHits(eq("土豆"), anyInt())).thenReturn(Collections.emptyList());

        globalSearchService.search("土豆", 999);
        verify(globalSearchMapper).selectCustomerHits("土豆", 30);
        verify(globalSearchMapper).selectProductHits("土豆", 30);
        verify(globalSearchMapper).selectOrderHits("土豆", 30);
    }

    @Test
    void 透传三组结果() {
        GlobalSearchVO.CustomerHit c = GlobalSearchVO.CustomerHit.builder().id(10L).name("丽宫").build();
        GlobalSearchVO.ProductHit p = GlobalSearchVO.ProductHit.builder().id(3L).code("S00000003").name("大白菜").build();
        GlobalSearchVO.OrderHit o = GlobalSearchVO.OrderHit.builder().id(99L).code("SO123").customerName("丽宫").build();
        when(globalSearchMapper.selectCustomerHits("丽", 5)).thenReturn(List.of(c));
        when(globalSearchMapper.selectProductHits("丽", 5)).thenReturn(List.of(p));
        when(globalSearchMapper.selectOrderHits("丽", 5)).thenReturn(List.of(o));

        GlobalSearchVO vo = globalSearchService.search("丽", 5);
        assertEquals(1, vo.getCustomers().size());
        assertEquals(1, vo.getProducts().size());
        assertEquals(1, vo.getOrders().size());
        assertEquals("大白菜", vo.getProducts().get(0).getName());
    }
}

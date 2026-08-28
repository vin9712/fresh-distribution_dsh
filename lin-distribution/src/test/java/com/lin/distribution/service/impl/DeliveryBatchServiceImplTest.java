package com.lin.distribution.service.impl;

import com.lin.distribution.mapper.DeliveryBatchMapper;
import com.lin.distribution.vo.DeliveryBatchViewVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 客户日总表视图测试（S14 §6.1/§八，D-027/D-028）：
 * 标准品名聚合 + 各配送点小计折叠、无价格不拆价、历史单回退明细行聚合。
 */
@ExtendWith(MockitoExtension.class)
class DeliveryBatchServiceImplTest {

    @Mock
    private DeliveryBatchMapper deliveryBatchMapper;

    @InjectMocks
    private DeliveryBatchServiceImpl service;

    private DeliveryBatchViewVO.Row row(Long skuId, String name, Long deptId, String deptName, String qty) {
        DeliveryBatchViewVO.Row row = new DeliveryBatchViewVO.Row();
        row.setSkuId(skuId);
        row.setProductName(name);
        row.setDeptId(deptId);
        row.setDeptName(deptName);
        row.setQuantity(new BigDecimal(qty));
        return row;
    }

    /** 同品跨点/跨价多行聚合成一个品名行，总量=各点合计，无价格字段拆行 */
    @Test
    void 按标准品名聚合各点小计() {
        when(deliveryBatchMapper.selectBatchViewRowsBySource(100L, "2026-08-28")).thenReturn(List.of(
                row(11L, "白菜", 201L, "A点", "5"),
                row(11L, "白菜", 202L, "B点", "3"),
                // 同一 sku 不同价（source_item 两行）不拆品名行
                row(11L, "白菜", 201L, "A点", "2"),
                row(12L, "土豆", 201L, "A点", "4")));

        List<DeliveryBatchViewVO> result = service.selectBatchView(100L, "2026-08-28");

        assertEquals(2, result.size());
        DeliveryBatchViewVO cabbage = result.get(0);
        assertEquals(11L, cabbage.getSkuId());
        assertEquals("白菜", cabbage.getProductName());
        assertEquals(0, new BigDecimal("10").compareTo(cabbage.getTotalQuantity()));
        assertEquals(2, cabbage.getDepts().size());
        assertEquals(0, new BigDecimal("7").compareTo(cabbage.getDepts().get(0).getQuantity()));
        assertEquals("A点", cabbage.getDepts().get(0).getDeptName());
        assertEquals(0, new BigDecimal("3").compareTo(cabbage.getDepts().get(1).getQuantity()));
        assertEquals("B点", cabbage.getDepts().get(1).getDeptName());
        assertEquals(0, new BigDecimal("4").compareTo(result.get(1).getTotalQuantity()));
    }

    /** 历史单回退：source_item 空时走送货明细行聚合；临时商品（无 sku）按品名归并 */
    @Test
    void 无台账历史单回退明细行聚合() {
        when(deliveryBatchMapper.selectBatchViewRowsBySource(100L, "2026-08-28")).thenReturn(List.of());
        when(deliveryBatchMapper.selectBatchViewRowsByDetail(100L, "2026-08-28")).thenReturn(List.of(
                row(null, "土鸡蛋", 201L, "A点", "10"),
                row(null, "土鸡蛋", 202L, "B点", "6")));

        List<DeliveryBatchViewVO> result = service.selectBatchView(100L, "2026-08-28");

        assertEquals(1, result.size());
        assertNull(result.get(0).getSkuId());
        assertEquals("土鸡蛋", result.get(0).getProductName());
        assertEquals(0, new BigDecimal("16").compareTo(result.get(0).getTotalQuantity()));
        assertEquals(2, result.get(0).getDepts().size());
    }

    /** 空数据：两路均为空返回空列表 */
    @Test
    void 无数据显示空列表() {
        when(deliveryBatchMapper.selectBatchViewRowsBySource(100L, "2026-08-28")).thenReturn(List.of());
        when(deliveryBatchMapper.selectBatchViewRowsByDetail(100L, "2026-08-28")).thenReturn(List.of());

        assertTrue(service.selectBatchView(100L, "2026-08-28").isEmpty());
    }
}

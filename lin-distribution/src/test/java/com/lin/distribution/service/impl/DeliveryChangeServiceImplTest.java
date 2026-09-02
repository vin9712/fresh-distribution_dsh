package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 配送后订单变更服务测试（D-055：加单/换货/退货标记）
 *
 * @author dsh
 */
@ExtendWith(MockitoExtension.class)
class DeliveryChangeServiceImplTest {

    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private SaleOrderDetailMapper saleOrderDetailMapper;

    @InjectMocks
    private DeliveryChangeServiceImpl deliveryChangeService;

    private SaleOrder confirmedOrder() {
        SaleOrder order = new SaleOrder();
        order.setId(100L);
        order.setStatus(SaleOrderStatus.CONFIRMED.getCode());
        order.setCustomerId(10L);
        order.setCustomerDeptId(2L);
        return order;
    }

    @Test
    void 加单_新增明细行标记加单且实收默认等于应送() {
        when(saleOrderMapper.selectSaleOrderById(100L)).thenReturn(confirmedOrder());
        when(saleOrderDetailMapper.selectSaleOrderDetailList(any())).thenReturn(new ArrayList<>());

        SaleOrderDetail detail = deliveryChangeService.addSupplement(100L, 88L, "大白菜", "", "斤",
                new BigDecimal("3"), new BigDecimal("1.50"), null, "现场加菜");

        assertEquals(1, detail.getChangeType().intValue());
        assertEquals(0, new BigDecimal("3").compareTo(detail.getNum()));
        assertEquals(0, new BigDecimal("3").compareTo(detail.getActualNum()), "实收默认=应送");
        assertEquals("现场加菜", detail.getChangeRemark());
    }

    @Test
    void 换货_原行标退货且新行同组标换货() {
        when(saleOrderMapper.selectSaleOrderById(100L)).thenReturn(confirmedOrder());
        SaleOrderDetail target = new SaleOrderDetail();
        target.setId(200L);
        target.setOrderId(100L);
        target.setSort(1);
        when(saleOrderDetailMapper.selectSaleOrderDetailById(200L)).thenReturn(target);

        List<SaleOrderDetail> result = deliveryChangeService.exchange(100L, 200L, 90L, "番茄", "", "斤",
                new BigDecimal("4"), new BigDecimal("4"), "土豆换番茄");

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getChangeType().intValue(), "换货新增行 change_type=2");
        assertEquals(3, result.get(1).getChangeType().intValue(), "被换行 change_type=3");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.get(1).getNum()), "被换行应收归0");
        assertEquals(result.get(0).getChangeGroup(), result.get(1).getChangeGroup(), "同组关联");
    }

    @Test
    void 退货_原行标记退货且应送实收归零() {
        when(saleOrderMapper.selectSaleOrderById(100L)).thenReturn(confirmedOrder());
        SaleOrderDetail target = new SaleOrderDetail();
        target.setId(300L);
        target.setOrderId(100L);
        target.setNum(new BigDecimal("5"));
        target.setActualNum(new BigDecimal("5"));
        when(saleOrderDetailMapper.selectSaleOrderDetailById(300L)).thenReturn(target);

        SaleOrderDetail result = deliveryChangeService.returnLine(100L, 300L, "客户退货");

        assertEquals(3, result.getChangeType().intValue());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getNum()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getActualNum()));
    }
}

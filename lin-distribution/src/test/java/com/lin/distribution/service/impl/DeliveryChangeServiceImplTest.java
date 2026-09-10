package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.lin.distribution.constant.AcceptanceStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.mapper.AcceptanceItemMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.AcceptanceService;
import com.lin.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Mock
    private com.lin.distribution.mapper.AcceptanceMapper acceptanceMapper;
    @Mock
    private AcceptanceItemMapper acceptanceItemMapper;
    @Mock
    private AcceptanceService acceptanceService;

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

    // ==================== OA：变更回退（revokeChange） ====================

    private SaleOrderDetail changeRow(Long id, int changeType, BigDecimal originalNum, Long group) {
        SaleOrderDetail d = new SaleOrderDetail();
        d.setId(id);
        d.setOrderId(100L);
        d.setProductName("土豆");
        d.setChangeType(changeType);
        d.setChangeOriginalNum(originalNum);
        d.setChangeGroup(group);
        return d;
    }

    private Acceptance accOf(Long id, Integer status) {
        Acceptance acc = new Acceptance();
        acc.setId(id);
        acc.setCode("YS20260901001");
        acc.setSaleOrderId(100L);
        acc.setStatus(status);
        return acc;
    }

    @Test
    void 加单回退_删除加单行并同步验收草稿() {
        when(saleOrderMapper.selectSaleOrderById(100L)).thenReturn(confirmedOrder());
        when(saleOrderDetailMapper.selectSaleOrderDetailById(300L)).thenReturn(changeRow(300L, 1, null, null));
        when(acceptanceMapper.selectBySaleOrder(100L)).thenReturn(accOf(12L, AcceptanceStatus.DRAFT.getCode()));

        deliveryChangeService.revokeChange(100L, 300L);

        verify(saleOrderDetailMapper).deleteSaleOrderDetailById(300L);
        verify(acceptanceItemMapper).deleteBySaleOrderDetailIds(12L, List.of(300L));
        verify(acceptanceService).syncMissingItems(12L);
    }

    @Test
    void 退货回退_按快照恢复原数量() {
        when(saleOrderMapper.selectSaleOrderById(100L)).thenReturn(confirmedOrder());
        SaleOrderDetail d = changeRow(301L, 3, new BigDecimal("5"), null);
        d.setProductPrice(new BigDecimal("2.00"));
        d.setExpectAmount(new BigDecimal("10.00"));
        when(saleOrderDetailMapper.selectSaleOrderDetailById(301L)).thenReturn(d);
        when(acceptanceMapper.selectBySaleOrder(100L)).thenReturn(null);

        deliveryChangeService.revokeChange(100L, 301L);

        ArgumentCaptor<SaleOrderDetail> captor = ArgumentCaptor.forClass(SaleOrderDetail.class);
        verify(saleOrderDetailMapper).updateSaleOrderDetail(captor.capture());
        assertEquals(0, captor.getValue().getChangeType().intValue(), "change_type 还原为 0");
        assertEquals(0, new BigDecimal("5").compareTo(captor.getValue().getNum()), "数量取变更前快照");
        assertEquals(0, new BigDecimal("5").compareTo(captor.getValue().getActualNum()));
        verify(acceptanceItemMapper, never()).deleteBySaleOrderDetailIds(any(), any());
        verify(acceptanceService, never()).syncMissingItems(any());
    }

    @Test
    void 换入行回退_整组恢复被换行并删换入行() {
        when(saleOrderMapper.selectSaleOrderById(100L)).thenReturn(confirmedOrder());
        SaleOrderDetail exchangeIn = changeRow(302L, 2, null, 777L);
        SaleOrderDetail returned = changeRow(301L, 3, new BigDecimal("5"), 777L);
        returned.setProductPrice(new BigDecimal("2.00"));
        returned.setExpectAmount(new BigDecimal("10.00"));
        when(saleOrderDetailMapper.selectSaleOrderDetailById(302L)).thenReturn(exchangeIn);
        when(saleOrderDetailMapper.selectValidByOrderIdForView(100L))
                .thenReturn(java.util.Arrays.asList(exchangeIn, returned));
        when(acceptanceMapper.selectBySaleOrder(100L)).thenReturn(null);

        deliveryChangeService.revokeChange(100L, 302L);

        verify(saleOrderDetailMapper).deleteSaleOrderDetailById(302L);
        ArgumentCaptor<SaleOrderDetail> captor = ArgumentCaptor.forClass(SaleOrderDetail.class);
        verify(saleOrderDetailMapper).updateSaleOrderDetail(captor.capture());
        assertEquals(301L, captor.getValue().getId(), "同组被换行被恢复");
        assertEquals(0, new BigDecimal("5").compareTo(captor.getValue().getNum()));
    }

    @Test
    void 已提交验收单时回退拒绝() {
        when(saleOrderMapper.selectSaleOrderById(100L)).thenReturn(confirmedOrder());
        when(saleOrderDetailMapper.selectSaleOrderDetailById(300L)).thenReturn(changeRow(300L, 1, null, null));
        when(acceptanceMapper.selectBySaleOrder(100L)).thenReturn(accOf(12L, AcceptanceStatus.SUBMITTED.getCode()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> deliveryChangeService.revokeChange(100L, 300L));
        assertTrue(ex.getMessage().contains("撤销验收"));
        verify(saleOrderDetailMapper, never()).deleteSaleOrderDetailById(any());
    }
}

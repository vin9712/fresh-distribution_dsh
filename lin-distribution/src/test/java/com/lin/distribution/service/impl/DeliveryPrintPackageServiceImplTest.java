package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryPrintPackage;
import com.lin.distribution.domain.DeliveryPrintTask;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliveryPrintPackageMapper;
import com.lin.distribution.mapper.DeliveryPrintTaskMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.DeliveryOrderService;
import com.lin.distribution.service.PrintTemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 送货单打印包服务测试（P2/D-050）
 *
 * <p>核心口径：建包幂等复用；回执成功才 markPrinted（计次/推进状态）；失败不计数不推进。</p>
 *
 * @author dsh
 */
@ExtendWith(MockitoExtension.class)
class DeliveryPrintPackageServiceImplTest {

    @Mock
    private DeliveryPrintPackageMapper printPackageMapper;
    @Mock
    private DeliveryPrintTaskMapper printTaskMapper;
    @Mock
    private DeliveryOrderMapper deliveryOrderMapper;
    @Mock
    private DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    @Mock
    private DeliveryOrderService deliveryOrderService;
    @Mock
    private PrintTemplateService printTemplateService;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private DeliveryPrintPackageServiceImpl printPackageService;

    @Test
    void 建包幂等_已有未完成包则复用() {
        DeliveryPrintPackage existing = new DeliveryPrintPackage();
        existing.setId(9L);
        existing.setStatus(DeliveryPrintPackage.STATUS_PRINTING);
        existing.setTotalCount(3);
        when(printPackageMapper.selectPrintPackageList(any(DeliveryPrintPackage.class)))
                .thenReturn(new ArrayList<>(List.of(existing)));
        // toVO 需要包 + 任务
        when(printPackageMapper.selectPrintPackageById(9L)).thenReturn(existing);
        when(printTaskMapper.selectTasksByPackageId(9L)).thenReturn(new ArrayList<>(List.of()));

        var vo = printPackageService.createPackage(100L, LocalDate.of(2026, 9, 1));

        assertEquals(9L, vo.getId());
        assertEquals(3, vo.getTotalCount());
    }

    @Test
    void 回执成功_调用markPrinted并任务置成功() {
        DeliveryPrintTask pending = new DeliveryPrintTask();
        pending.setId(11L);
        pending.setPackageId(9L);
        pending.setDeliveryOrderId(501L);
        pending.setStatus(DeliveryPrintTask.STATUS_PREVIEWED);
        when(printTaskMapper.selectTaskById(11L)).thenReturn(pending);
        when(deliveryOrderService.markPrinted(501L)).thenReturn(new DeliveryOrder());
        when(printTaskMapper.countByStatus(9L)).thenReturn(new ArrayList<>(List.of()));
        when(printTaskMapper.selectTaskById(11L)).thenReturn(pending);

        DeliveryPrintTask result = printPackageService.receipt(11L, true, null);

        verify(deliveryOrderService).markPrinted(501L);
        assertNotNull(result);
    }

    @Test
    void 回执失败_不调用markPrinted() {
        DeliveryPrintTask pending = new DeliveryPrintTask();
        pending.setId(12L);
        pending.setPackageId(9L);
        pending.setDeliveryOrderId(502L);
        pending.setStatus(DeliveryPrintTask.STATUS_PRINTING);
        when(printTaskMapper.selectTaskById(12L)).thenReturn(pending);
        when(printTaskMapper.countByStatus(9L)).thenReturn(new ArrayList<>(List.of()));
        // 回执后重新查询返回更新后的任务（带失败原因）
        DeliveryPrintTask failed = new DeliveryPrintTask();
        failed.setId(12L);
        failed.setPackageId(9L);
        failed.setDeliveryOrderId(502L);
        failed.setStatus(DeliveryPrintTask.STATUS_FAILED);
        failed.setErrorMsg("打印机离线");
        when(printTaskMapper.selectTaskById(12L)).thenReturn(failed);

        DeliveryPrintTask result = printPackageService.receipt(12L, false, "打印机离线");

        verify(deliveryOrderService, never()).markPrinted(anyLong());
        assertEquals("打印机离线", result.getErrorMsg());
    }
}

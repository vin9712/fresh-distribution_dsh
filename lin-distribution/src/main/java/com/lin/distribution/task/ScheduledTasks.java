package com.lin.distribution.task;

import java.time.LocalDate;

import com.lin.distribution.dto.PurchaseGenerateDTO;
import com.lin.distribution.service.ProductSkuQuoteService;
import com.lin.distribution.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务（D-055：送货单视图化后不再需要生成定时任务——数据源=订单，日总表/点单实时聚合）
 *
 * <p>保留：报价状态同步、采购单自动生成（与送货单无关）。</p>
 *
 * @author vinga
 * @date 2024/11/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final ProductSkuQuoteService productSkuQuoteService;
    private final PurchaseOrderService purchaseOrderService;

    @Scheduled(cron = "0 0 12 * * ?")
    public void syncUpdateQuoteStatus() {
        LocalDate now = LocalDate.now();
        log.info("[sync update quote status] start, date:{}", now);
        try {
            productSkuQuoteService.syncUpdateQuoteStatus();
        } catch (Exception e) {
            log.error("[sync update quote status] error:{}", e.getMessage(), e);
        }
    }

    /**
     * 每日 23:00 自动生成明日配送订单的采购单
     */
    @Scheduled(cron = "0 0 23 * * ?")
    public void generateTomorrowPurchaseOrder() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        log.info("[generate tomorrow purchase order] start, date:{}", tomorrow);
        try {
            PurchaseGenerateDTO dto = new PurchaseGenerateDTO();
            dto.setOrderDate(tomorrow);
            purchaseOrderService.generateByOrderDate(dto);
        } catch (Exception e) {
            log.error("[generate tomorrow purchase order] error:{}", e.getMessage(), e);
        }
    }
}

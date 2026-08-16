package com.lin.distribution.task;

import com.lin.distribution.dto.PurchaseGenerateDTO;
import com.lin.distribution.service.DeliveryOrderService;
import com.lin.distribution.service.ProductSkuQuoteService;
import com.lin.distribution.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * @author vinga
 * @date 2024/11/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final ProductSkuQuoteService productSkuQuoteService;
    private final PurchaseOrderService purchaseOrderService;
    private final DeliveryOrderService deliveryOrderService;

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

    /**
     * D+1 清晨 06:00 自动生成当日送货单（已生成则幂等跳过）
     */
    @Scheduled(cron = "0 0 6 * * ?")
    public void generateTodayDeliveryOrder() {
        LocalDate today = LocalDate.now();
        log.info("[generate today delivery order] start, date:{}", today);
        try {
            deliveryOrderService.generateByDeliveryDate(today);
        } catch (Exception e) {
            // 幂等冲突（已生成）仅记录日志，不中断定时任务
            log.warn("[generate today delivery order] skipped, date:{}, reason:{}", today, e.getMessage());
        }
    }
}

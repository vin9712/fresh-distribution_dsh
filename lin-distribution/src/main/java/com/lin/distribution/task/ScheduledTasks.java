package com.lin.distribution.task;

import com.lin.distribution.service.ProductSkuQuoteService;
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
}

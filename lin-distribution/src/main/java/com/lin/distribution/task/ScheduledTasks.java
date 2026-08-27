package com.lin.distribution.task;

import java.time.LocalDate;

import com.lin.common.utils.DateUtils;
import com.lin.distribution.constant.DeliveryGenerateTrigger;
import com.lin.distribution.domain.JobRunLog;
import com.lin.distribution.dto.PurchaseGenerateDTO;
import com.lin.distribution.mapper.JobRunLogMapper;
import com.lin.distribution.service.DeliveryGenerationService;
import com.lin.distribution.service.ProductSkuQuoteService;
import com.lin.distribution.service.PurchaseOrderService;
import com.lin.distribution.vo.GenerateResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务（S14/T3：送货单生成双窗口，C2 定稿）
 *
 * <p>业务口径（2026-08-28 确认）：送货单以手工生成为主路径（录单页「本客户订单已录完」/按客户生成），
 * 定时窗口仅兜底扫遗漏；两窗口共用统一生成服务 generateForDate(SCHEDULED)，幂等可重复触发，
 * 运行结果写 t_job_run_log 供工作台告警（Q36/D-037）。</p>
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
    private final DeliveryGenerationService deliveryGenerationService;
    private final JobRunLogMapper jobRunLogMapper;

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
     * C2 双窗口之一：每日 23:30 预生成次日配送批次+送货单（文员下班前即发现漏单）
     */
    @Scheduled(cron = "0 30 23 * * ?")
    public void generateTomorrowDeliveryOrder() {
        LocalDate target = LocalDate.now().plusDays(1);
        log.info("[generate tomorrow delivery order] start, date:{}", target);
        runDeliveryGenerate(target);
    }

    /**
     * C2 双窗口之二：每日 06:00 兜底，自动补齐当日夜间新确认的遗漏订单（幂等，走补单规则）
     */
    @Scheduled(cron = "0 0 6 * * ?")
    public void generateTodayDeliveryOrder() {
        LocalDate target = LocalDate.now();
        log.info("[generate today delivery order] start, date:{}", target);
        runDeliveryGenerate(target);
    }

    /**
     * 调统一生成服务执行窗口任务；服务内已按客户隔离并写 job_run_log（成功/失败/部分失败），
     * 此处兜服务级意外失败，确保告警数据源不缺失。
     */
    private void runDeliveryGenerate(LocalDate bizDate) {
        try {
            GenerateResultVO result = deliveryGenerationService.generateForDate(bizDate, DeliveryGenerateTrigger.SCHEDULED);
            log.info("[delivery generate] date:{}, created:{}, skipped:{}", bizDate,
                    result.getCreatedOrders().size(), result.getSkippedReasons().size());
        } catch (Exception e) {
            log.error("[delivery generate] date:{} failed", bizDate, e);
            try {
                JobRunLog jobRunLog = new JobRunLog();
                jobRunLog.setJobName(DeliveryGenerationService.JOB_NAME_DELIVERY_GENERATE);
                jobRunLog.setBizDate(bizDate);
                jobRunLog.setStatus(JobRunLog.STATUS_FAILED);
                jobRunLog.setMessage(StringUtils.abbreviate(e.getMessage(), 500));
                jobRunLog.setWarningCount(0);
                jobRunLog.setRunTime(DateUtils.getNowDate());
                jobRunLogMapper.insertJobRunLog(jobRunLog);
            } catch (Exception ex) {
                log.error("[delivery generate] write failed job log error:{}", ex.getMessage(), ex);
            }
        }
    }
}

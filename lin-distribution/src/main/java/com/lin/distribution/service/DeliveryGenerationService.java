package com.lin.distribution.service;

import java.time.LocalDate;

import com.lin.distribution.constant.DeliveryGenerateTrigger;
import com.lin.distribution.dto.DeliveryByOrdersDTO;
import com.lin.distribution.vo.GenerateResultVO;

/**
 * 送货单统一生成服务（S14/T3，DESIGN.md §5.1 / D-021）
 *
 * <p>定时任务、手工按客户生成、按勾选订单生成三个入口共用同一服务与幂等规则；
 * 三态分支：无既有单→正常生成 / 全部未打印→作废重建（D-022）/ 含已打印→补充单（D-023）。
 * 每客户一个事务，任一步失败整体回滚，批次不留半截数据。</p>
 *
 * @author dsh
 */
public interface DeliveryGenerationService {

    /** t_job_run_log.job_name（Q36/D-037 工作台告警数据源） */
    String JOB_NAME_DELIVERY_GENERATE = "DELIVERY_GENERATE";

    /**
     * 幂等生成某配送日期全部客户的批次+送货单。
     * 定时窗口（23:30 预生成次日 / 06:00 兜底当日）与旧按日期入口委托共用；
     * 单客户失败不影响其他客户，SCHEDULED 触发时运行结果写 t_job_run_log。
     *
     * @param deliveryDate 配送日期
     * @param trigger      触发来源
     * @return 生成结果（createdOrders 聚合全部客户）
     */
    GenerateResultVO generateForDate(LocalDate deliveryDate, DeliveryGenerateTrigger trigger);

    /**
     * 手工按客户+日期补齐全部遗漏订单（D-025：不接受订单子集参数）。
     * 幂等：无遗漏订单直接返回；未打印补单=作废重建（D-022）；已打印补单=补充单（D-023）。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 生成结果
     */
    GenerateResultVO generateForCustomer(Long customerId, LocalDate deliveryDate);

    /**
     * 按勾选订单生成（旧入口 /generate-by-orders 兼容委托）。
     * D-025 语义：勾选仅用于定位 客户+配送日期（未传日期时逐单取自身配送日期），
     * 实际按客户+日期补齐全部遗漏订单，而非仅勾选子集。
     *
     * @param dto 勾选订单请求
     * @return 生成结果
     */
    GenerateResultVO generateForOrders(DeliveryByOrdersDTO dto);
}

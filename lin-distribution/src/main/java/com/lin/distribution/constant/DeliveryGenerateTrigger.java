package com.lin.distribution.constant;

/**
 * 送货单生成触发来源（S14/T3 统一生成服务，DESIGN.md §5.1 / D-021）
 *
 * <p>业务口径（2026-08-28 确认）：送货单以<b>手工生成为主路径</b>（录单页「本客户订单已录完」
 * 按钮 &gt; 订单列表勾选/按客户生成），23:30/06:00 定时窗口仅作兜底扫遗漏。</p>
 *
 * @author dsh
 */
public enum DeliveryGenerateTrigger {
    /** 定时窗口（23:30 预生成次日 / 06:00 兜底当日），运行结果写 t_job_run_log 供工作台告警 */
    SCHEDULED,
    /** 手工入口（按客户+日期生成/补单、旧按日期入口委托） */
    MANUAL,
    /** 手工补单入口（语义与 MANUAL 一致，预留区分来源） */
    SUPPLEMENT

    ;
}

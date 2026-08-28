package com.lin.distribution.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 待验收提醒级别计算（蓝图 W0-3.2）：
 * <ul>
 *   <li>2 红色：配送日当天 11:30 后仍未验收，或配送日已过期（delivery_date &lt; today）；</li>
 *   <li>1 黄色：配送日当天 11:30 前、打印满 2 小时仍未验收；</li>
 *   <li>0 无：其余（未到打印满 2h / 未来配送日）。</li>
 * </ul>
 *
 * @author dsh
 */
public final class PendingAcceptanceReminder {

    /** 红色：配送日当天 11:30 */
    private static final LocalTime RED_TIME = LocalTime.of(11, 30);
    /** 黄色：打印满 2 小时 */
    private static final Duration YELLOW_DURATION = Duration.ofHours(2);

    public static final int LEVEL_NONE = 0;
    public static final int LEVEL_YELLOW = 1;
    public static final int LEVEL_RED = 2;

    private PendingAcceptanceReminder() {
    }

    /**
     * 计算提醒级别（可注入 today/now 便于单测）\n
     *
     * @param deliveryDate 配送日期
     * @param printTime    最近打印时间
     * @param today        今天（用于判定配送日）
     * @param now          当前时刻
     * @return 0无 1黄 2红
     */
    public static int compute(LocalDate deliveryDate, Date printTime, LocalDate today, LocalTime now) {
        if (deliveryDate == null || today == null || now == null) {
            return LEVEL_NONE;
        }
        if (deliveryDate.isBefore(today)) {
            return LEVEL_RED; // 已过配送日仍未验收 → 过期红
        }
        if (deliveryDate.isAfter(today)) {
            return LEVEL_NONE; // 未来配送日
        }
        // 配送日当天
        if (!now.isBefore(RED_TIME)) {
            return LEVEL_RED; // 11:30 后
        }
        if (printTime != null) {
            LocalDateTime printLdt = LocalDateTime.ofInstant(printTime.toInstant(), ZoneId.systemDefault());
            if (Duration.between(printLdt, now.atDate(today)).compareTo(YELLOW_DURATION) >= 0) {
                return LEVEL_YELLOW; // 打印满 2 小时
            }
        }
        return LEVEL_NONE;
    }

    public static String reason(int level) {
        switch (level) {
            case LEVEL_RED:
                return "配送日当天 11:30 后仍未验收";
            case LEVEL_YELLOW:
                return "打印满 2 小时仍未验收";
            default:
                return null;
        }
    }
}

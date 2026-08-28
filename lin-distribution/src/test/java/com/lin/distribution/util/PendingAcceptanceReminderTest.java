package com.lin.distribution.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 待验收提醒级别计算测试（蓝图 W0-3.2：打印满2h→黄 / 当日11:30后→红 / 过期→红）
 */
class PendingAcceptanceReminderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);

    private Date at(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void 当日11点半前且未满2小时为无提醒() {
        // 下单打印 8/28 10:00，当前 11:00（未满2h）
        int level = PendingAcceptanceReminder.compute(TODAY, at(LocalDateTime.of(2026, 8, 28, 10, 0)),
                TODAY, LocalTime.of(11, 0));
        assertEquals(PendingAcceptanceReminder.LEVEL_NONE, level);
    }

    @Test
    void 当日打印满2小时为黄色() {
        // 打印 8/28 09:00，当前 11:00（满2h，未到11:30）
        int level = PendingAcceptanceReminder.compute(TODAY, at(LocalDateTime.of(2026, 8, 28, 9, 0)),
                TODAY, LocalTime.of(11, 0));
        assertEquals(PendingAcceptanceReminder.LEVEL_YELLOW, level);
    }

    @Test
    void 当日11点半后为红色() {
        // 打印 8/28 10:00，当前 11:40（已过11:30）
        int level = PendingAcceptanceReminder.compute(TODAY, at(LocalDateTime.of(2026, 8, 28, 10, 0)),
                TODAY, LocalTime.of(11, 40));
        assertEquals(PendingAcceptanceReminder.LEVEL_RED, level);
    }

    @Test
    void 已过配送日期为红色() {
        int level = PendingAcceptanceReminder.compute(TODAY.minusDays(1), null, TODAY, LocalTime.of(9, 0));
        assertEquals(PendingAcceptanceReminder.LEVEL_RED, level);
    }

    @Test
    void 未来配送日期为无提醒() {
        int level = PendingAcceptanceReminder.compute(TODAY.plusDays(1), null, TODAY, LocalTime.of(12, 0));
        assertEquals(PendingAcceptanceReminder.LEVEL_NONE, level);
    }
}

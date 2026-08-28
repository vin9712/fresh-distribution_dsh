package com.lin.distribution.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 下月调整单状态（蓝图 W0-2.7）：草稿 → 已提交（提交后参与对账/经营概览重算）
 *
 * @author dsh
 */
@Getter
@AllArgsConstructor
public enum MonthAdjustmentStatus {
    DRAFT(0, "草稿"),
    SUBMITTED(1, "已提交"),

    ;

    private final Integer code;
    private final String desc;
}

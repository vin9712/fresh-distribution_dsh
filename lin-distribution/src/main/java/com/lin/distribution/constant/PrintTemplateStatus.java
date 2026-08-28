package com.lin.distribution.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 打印模板状态（蓝图 W0-4.4）：草稿 → 已测试 → 已发布；已发布版本在下一版本发布前继续使用
 *
 * @author dsh
 */
@Getter
@AllArgsConstructor
public enum PrintTemplateStatus {
    DRAFT(0, "草稿"),
    TESTED(1, "已测试"),
    PUBLISHED(2, "已发布"),

    ;

    private final Integer code;
    private final String desc;
}

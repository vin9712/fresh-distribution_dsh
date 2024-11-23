package com.lin.distribution.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author vinga
 * @date 2024/11/23
 */
@Getter
@AllArgsConstructor
public enum ProductSkuQuoteStatus {
    NEW(0, "新增"),
    PUBLISHED(1, "发布"),
    INVALID(2, "失效"),

    ;

    private final Integer code;
    private final String desc;

}

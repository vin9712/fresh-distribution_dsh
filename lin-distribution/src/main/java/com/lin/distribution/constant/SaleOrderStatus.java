package com.lin.distribution.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author vinga
 * @date 2024/12/03
 */
@Getter
@AllArgsConstructor
public enum SaleOrderStatus {
    NEW(0, "制单"),
    REVIEWED(1, "审核"),
    DELIVERED(2, "送货"),
    CHECKED(3, "验收"),
    FINISHED(4, "完成"),

    ;

    private final Integer code;
    private final String desc;

}

package com.lin.distribution.constant;

import com.lin.common.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

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

    public static SaleOrderStatus fromCode(Integer code) {
        for (SaleOrderStatus status : SaleOrderStatus.values()) {
            if (Objects.equals(status.getCode(), code)) {
                return status;
            }
        }
        throw new ServiceException("invalid sale order status code");
    }

}

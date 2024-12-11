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
public enum DeliveryOrderStatus {
    PENDING(0, "待打印"),
    DELIVERED(1, "送货"),
    FINISHED(2, "完成"),

    ;

    private final Integer code;
    private final String desc;

    public static DeliveryOrderStatus fromCode(Integer code) {
        for (DeliveryOrderStatus status : DeliveryOrderStatus.values()) {
            if (Objects.equals(status.getCode(), code)) {
                return status;
            }
        }
        throw new ServiceException("invalid delivery order status code");
    }

}

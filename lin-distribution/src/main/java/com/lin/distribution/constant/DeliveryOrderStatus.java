package com.lin.distribution.constant;

import com.lin.common.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 送货单状态（DESIGN.md §7.2：待打印 → 已打印 → 已送达）
 * 枚举码 0-2 与现有库表数据保持一致，仅升级语义与文案。
 *
 * @author dsh
 */
@Getter
@AllArgsConstructor
public enum DeliveryOrderStatus {
    PENDING(0, "待打印"),
    PRINTED(1, "已打印"),
    DELIVERED(2, "已送达"),

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

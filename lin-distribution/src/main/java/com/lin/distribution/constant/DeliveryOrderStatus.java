package com.lin.distribution.constant;

import com.lin.common.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 送货单状态（DESIGN.md §7.2：待打印 → 已打印 → 已送达；S14 扩展：已作废）
 * 枚举码与现有库表数据保持一致，仅升级语义与文案。
 *
 * @author dsh
 */
@Getter
@AllArgsConstructor
public enum DeliveryOrderStatus {
    PENDING(0, "待打印"),
    PRINTED(1, "已打印"),
    DELIVERED(2, "已送达"),
    /** S14：作废后来源分配已释放，可重新生成；作废单仅可查看不可打印/送达 */
    VOIDED(3, "已作废"),

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

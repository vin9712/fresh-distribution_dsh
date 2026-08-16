package com.lin.distribution.constant;

import com.lin.common.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 销售订单状态（DESIGN.md §7.1：DRAFT → CONFIRMED → DELIVERED → ACCEPTED → SETTLED）
 * 枚举码 0-4 与现有库表数据保持一致，仅升级语义与文案。
 *
 * @author dsh
 */
@Getter
@AllArgsConstructor
public enum SaleOrderStatus {
    DRAFT(0, "草稿"),
    CONFIRMED(1, "已确认"),
    DELIVERED(2, "已配送"),
    ACCEPTED(3, "已验收"),
    SETTLED(4, "已结算"),

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

package com.lin.distribution.constant;

import com.lin.common.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 采购单状态（DESIGN.md §7.3：草稿 → 已确认 → 已入库）
 *
 * @author dsh
 */
@Getter
@AllArgsConstructor
public enum PurchaseOrderStatus {
    DRAFT(0, "草稿"),
    CONFIRMED(1, "已确认"),
    STOCKED(2, "已入库"),

    ;

    private final Integer code;
    private final String desc;

    public static PurchaseOrderStatus fromCode(Integer code) {
        for (PurchaseOrderStatus status : PurchaseOrderStatus.values()) {
            if (Objects.equals(status.getCode(), code)) {
                return status;
            }
        }
        throw new ServiceException("invalid purchase order status code");
    }
}

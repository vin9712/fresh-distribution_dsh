package com.lin.distribution.constant;

import com.lin.common.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 退货单状态（S14/T6，D-032：独立退货单，不撤回历史验收）
 * 0草稿 → 1已提交(质检中) → 2质检完成；3已完成由结算模块落（本期不开放写入口）
 *
 * @author dsh
 */
@Getter
@AllArgsConstructor
public enum ReturnOrderStatus {
    DRAFT(0, "草稿"),
    SUBMITTED(1, "已提交(质检中)"),
    INSPECTED(2, "质检完成"),
    COMPLETED(3, "已完成"),

    ;

    private final Integer code;
    private final String desc;

    public static ReturnOrderStatus fromCode(Integer code) {
        for (ReturnOrderStatus status : ReturnOrderStatus.values()) {
            if (Objects.equals(status.getCode(), code)) {
                return status;
            }
        }
        throw new ServiceException("invalid return order status code");
    }
}

package com.lin.distribution.constant;

import com.lin.common.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 验收单状态（DESIGN.md §9：0草稿 1已提交；一单一验）
 *
 * @author dsh
 */
@Getter
@AllArgsConstructor
public enum AcceptanceStatus {
    DRAFT(0, "草稿"),
    SUBMITTED(1, "已提交"),
    ;

    private final Integer code;
    private final String desc;

    public static AcceptanceStatus fromCode(Integer code) {
        for (AcceptanceStatus status : AcceptanceStatus.values()) {
            if (Objects.equals(status.getCode(), code)) {
                return status;
            }
        }
        throw new ServiceException("invalid acceptance status code");
    }
}

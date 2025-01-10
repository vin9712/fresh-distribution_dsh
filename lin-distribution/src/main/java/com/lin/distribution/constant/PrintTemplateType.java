package com.lin.distribution.constant;

import com.lin.common.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@Getter
@AllArgsConstructor
public enum PrintTemplateType {
    DELIVERY(0, "送货单"),
    SUMMARY(1, "汇总单"),

    ;

    private final Integer code;
    private final String desc;

    public static PrintTemplateType fromCode(Integer code) {
        for (PrintTemplateType type : PrintTemplateType.values()) {
            if (Objects.equals(type.getCode(), code)) {
                return type;
            }
        }
        throw new ServiceException("invalid print template type code");
    }
}

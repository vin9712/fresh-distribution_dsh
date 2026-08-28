package com.lin.distribution.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 送货单打印拆分配置 — 拆分方式（W0-2.2，蓝图「送货合单/打印拆分规则」）
 *
 * <p>描述该送货单如何由订单聚合而成并落成打印结构，随批次组单策略快照：
 * <ul>
 *   <li>DEFAULT_PER_DEPT：默认一配送点一张（对应批次 scope_type=DELIVERY_POINT_DATE）；</li>
 *   <li>CROSS_POINT_MERGE：跨配送点合单（对应批次 scope_type=CUSTOMER_DATE），仅允许 A4 输出；</li>
 *   <li>MAX_ROWS_SPLIT：按最大行数拆分（例外策略，由送货单配置处理）。</li>
 * </ul></p>
 *
 * @author dsh
 */
@Getter
@AllArgsConstructor
public enum DeliveryPrintSplitMode {
    /** 默认按配送点一张 */
    DEFAULT_PER_DEPT("DEFAULT_PER_DEPT", "默认按配送点"),
    /** 跨配送点合单（仅 A4） */
    CROSS_POINT_MERGE("CROSS_POINT_MERGE", "跨点合单(仅A4)"),
    /** 按最大行数拆分 */
    MAX_ROWS_SPLIT("MAX_ROWS_SPLIT", "按最大行数拆分"),
    ;

    private final String code;
    private final String desc;

    /**
     * 从批次组单范围快照推导拆分方式：CUSTOMER_DATE（A类总单）→ 跨点合单；
     * 其余（DELIVERY_POINT_DATE 等）→ 默认按配送点。
     */
    public static DeliveryPrintSplitMode fromScopeType(String scopeType) {
        return DeliveryScopeType.isCustomerDate(scopeType)
                ? CROSS_POINT_MERGE
                : DEFAULT_PER_DEPT;
    }

    /**
     * 编码是否存在合法值（用于配置校验/归一化）。
     */
    public static boolean isValid(String code) {
        for (DeliveryPrintSplitMode mode : DeliveryPrintSplitMode.values()) {
            if (Objects.equals(mode.getCode(), code)) {
                return true;
            }
        }
        return false;
    }
}

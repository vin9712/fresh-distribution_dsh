package com.lin.distribution.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 送货单打印拆分配置 — 输出介质（W0-2.2，蓝图「A4 输出/针式纸张/针式合单边界」）
 *
 * <p>打印模板只声明版式与纸张，介质决定了输出设备与分页规则：
 * <ul>
 *   <li>A4：HP 激光打印，A4 纵向单面，按标准页面高度自动分页；跨点合单仅允许 A4；</li>
 *   <li>DOT_MATRIX：Epson 针式预印多联连续纸，仅面向单配送点，固定每页 10 条并重复单据头。</li>
 * </ul></p>
 *
 * @author dsh
 */
@Getter
@AllArgsConstructor
public enum DeliveryPrintMediaType {
    /** A4 激光打印 */
    A4("A4", "A4激光打印"),
    /** 针式多联打印 */
    DOT_MATRIX("DOT_MATRIX", "针式多联打印"),
    ;

    private final String code;
    private final String desc;

    /**
     * 编码是否存在合法值（用于配置校验/归一化）。
     */
    public static boolean isValid(String code) {
        for (DeliveryPrintMediaType mediaType : DeliveryPrintMediaType.values()) {
            if (Objects.equals(mediaType.getCode(), code)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDotMatrix(String code) {
        return DOT_MATRIX.getCode().equals(code);
    }
}

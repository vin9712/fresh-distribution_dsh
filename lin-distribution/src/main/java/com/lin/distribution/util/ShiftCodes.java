package com.lin.distribution.util;

import com.lin.common.utils.DictUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 配送点班次工具（s35《配送点班次配置》）。
 *
 * <p>班次是配送点的<b>可选维度</b>，只对启用班次的客户（{@code t_customer.shift_enabled=1}）生效：
 * 关闭时全链路（下单校验、矩阵列、总单表头）忽略班次，行为与引入前完全一致。</p>
 *
 * <ul>
 *   <li>配送点声明自己支持哪些班次：{@code t_customer_dept.shift_codes}（字典 {@value #DICT_TYPE} 值逗号分隔，空=不分班次）；</li>
 *   <li>订单落所选班次：{@code t_sale_order.shift_code}；</li>
 *   <li>矩阵列 = 配送点 × 班次，列名 = 点名 + 班次名（如「华铃」+「白班」=「华铃白班」）；</li>
 *   <li>历史无班次单按业务口径归白班（{@link #orDefault}），不改写历史行。</li>
 * </ul>
 *
 * @author dsh
 */
public final class ShiftCodes {
    /** 白班：历史无班次单归入此班次（业务确认 2026-09-18） */
    public static final String DAY = "DAY";
    /** 夜班 */
    public static final String NIGHT = "NIGHT";
    /** 班次字典类型 */
    public static final String DICT_TYPE = "biz_shift_type";

    private ShiftCodes() {
    }

    /** 解析配送点声明的班次列表（去空、去重、保持声明顺序） */
    public static List<String> parse(String shiftCodes) {
        if (StringUtils.isBlank(shiftCodes)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(Arrays.stream(shiftCodes.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toList()));
    }

    /**
     * 历史无班次单归白班：空值返回 {@link #DAY}，其余原样返回。
     * 用于矩阵列归属与订单编辑时的快照比对，避免为历史数据造出「未分班次」列。
     */
    public static String orDefault(String shiftCode) {
        return StringUtils.isBlank(shiftCode) ? DAY : shiftCode.trim();
    }

    /**
     * 班次中文名：字典（{@value #DICT_TYPE}）优先，缓存/上下文不可用时回落内置标签，
     * 保证打印表头在任何环境下都出中文而不是裸编码。
     */
    public static String label(String shiftCode) {
        if (StringUtils.isBlank(shiftCode)) {
            return "";
        }
        String code = shiftCode.trim();
        try {
            String label = DictUtils.getDictLabel(DICT_TYPE, code);
            if (StringUtils.isNotBlank(label)) {
                return label;
            }
        } catch (Exception ignored) {
            // 无字典缓存（单测/离线打印）时走内置标签
        }
        return switch (code) {
            case DAY -> "白班";
            case NIGHT -> "夜班";
            default -> code;
        };
    }

    /**
     * 矩阵列/格键：{@code deptId}（无班次）或 {@code deptId#班次}（启用班次）。
     * 无班次时键就是纯 deptId，非班次客户的对外契约（cells 键、列定位）与引入前完全一致。
     */
    public static String cellKey(Long deptId, String shiftCode) {
        if (deptId == null) {
            return "";
        }
        return StringUtils.isBlank(shiftCode) ? String.valueOf(deptId) : deptId + "#" + shiftCode.trim();
    }

    /**
     * 矩阵列名：配送点名 + 班次名。班次为空时即点名本身（未启用班次的客户列名不变）。
     */
    public static String columnName(String deptName, String shiftCode) {
        String name = StringUtils.defaultString(deptName);
        String label = label(shiftCode);
        return label.isEmpty() ? name : name + label;
    }
}

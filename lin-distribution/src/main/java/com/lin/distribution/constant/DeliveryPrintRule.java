package com.lin.distribution.constant;

/**
 * 送货单打印拆分配置 — 默认规则常量（W0-2.2，蓝图「打印拆分规则/针式分页/拆单标识」）
 *
 * <p>集中定义打印拆分的默认值与边界，供配置解析与校验共用，避免散落硬编码：</p>
 * <ul>
 *   <li>DEFAULT_SPLIT_MODE：默认一配送点一张；</li>
 *   <li>DEFAULT_MEDIA_TYPE：默认 A4（客户打印默认每配送点一张 A4）；</li>
 *   <li>DOT_MATRIX_ROWS_PER_PAGE：针式单点固定每页 10 条；</li>
 *   <li>DEFAULT_ROWS_PER_PAGE：未指定分页行数时的兜底值；</li>
 *   <li>MAX_ROWS_ALLOWED_PER_PAGE：分页行数上限（防御异常配置）。</li>
 * </ul>
 *
 * @author dsh
 */
public final class DeliveryPrintRule {

    /** 默认拆分方式：默认按配送点一张 */
    public static final String DEFAULT_SPLIT_MODE = DeliveryPrintSplitMode.DEFAULT_PER_DEPT.getCode();

    /** 默认输出介质：A4（客户打印默认每配送点一张 A4 单据） */
    public static final String DEFAULT_MEDIA_TYPE = DeliveryPrintMediaType.A4.getCode();

    /** 针式单点固定每页 10 条明细（蓝图「针式分页」） */
    public static final int DOT_MATRIX_ROWS_PER_PAGE = 10;

    /** 未指定分页行数时的兜底值 */
    public static final int DEFAULT_ROWS_PER_PAGE = DOT_MATRIX_ROWS_PER_PAGE;

    /** 分页行数上限（防御手工异常配置） */
    public static final int MAX_ROWS_PER_PAGE = 50;

    private DeliveryPrintRule() {
    }
}

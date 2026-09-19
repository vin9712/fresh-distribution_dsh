package com.lin.distribution.service.support;

import java.util.List;

/**
 * 打印数据契约（PR-D3，《打印模块重构设计》§3.3）——把
 * {@code docs/01-design/打印模板通用数据结构与模板设计.md} 的字段字典**可执行化**。
 *
 * <p>唯一数据口径：模板生成器、物化器、预览字段面板、设计器字段面板共用本类，
 * 杜绝「后端改了字段、模板/文档没跟上」的漂移。字段名严格取自
 * {@code PrintController} 的真实输出（不是设计文档里过时的 {@code spec/unit}）。</p>
 *
 * <p>形态：</p>
 * <ul>
 *   <li>{@code MATRIX}（总单·跨点矩阵）：数据集 {@code hm}(表头) / {@code hc}(列定义) /
 *       {@code dc}(全交叉长表) 或 {@code dm}(槽位宽表)；</li>
 *   <li>{@code FLAT}（点单平铺）：数据集 {@code hd}(表头) / {@code dd}(明细)。</li>
 * </ul>
 *
 * @author dsh
 */
public final class PrintDataContract {

    /** 打印形态 */
    public enum Form { MATRIX, FLAT }

    /** 总单行形态：WIDE=槽位宽表（套打）/ LONG=全交叉长表（横向动态列） */
    public enum RowsShape { WIDE, LONG }

    /** 字段所属数据集分节 */
    public enum Section { HEAD, COLUMNS, ROWS }

    /**
     * 字段定义
     *
     * @param code    字段编码（模板绑定 {@code #{ds.code}} / {@code ${ds.code}}）
     * @param text    中文名（设计器/字段面板展示）
     * @param type    建议类型（String / BigDecimal / Boolean / Object）
     * @param section 所属分节
     */
    public record Field(String code, String text, String type, Section section) {}

    /**
     * 数据集参数（URL 同名透传，{@code search_flag=0} 不渲染查询控件）
     */
    public record Param(String code, String text, String defaultValue) {}

    /**
     * 数据集定义
     *
     * @param dbCode    数据集编码（hd/dd/hm/hc/dc/dm）
     * @param chName    数据集中文名
     * @param list      是否列表数据集（is_list）
     * @param apiConvert 转换器 bean 名
     * @param endpoint  取数端点（不含 base url 与 query）
     * @param shape     行形态（仅 MATRIX 的 dc/dm 有意义；其余为 null）
     */
    public record DataSet(String dbCode, String chName, boolean list, String apiConvert,
                          String endpoint, RowsShape shape) {}

    // ==================== 字段字典 ====================

    private static final List<Field> MATRIX_HEAD = List.of(
            f("printTitle", "打印标题", "String", Section.HEAD),
            f("bizKey", "打印主体键", "String", Section.HEAD),
            f("customerId", "客户ID", "BigDecimal", Section.HEAD),
            f("customerName", "客户名称", "String", Section.HEAD),
            f("deliveryDate", "配送日期", "String", Section.HEAD),
            f("code", "单号", "String", Section.HEAD),
            f("docKind", "单据类型", "String", Section.HEAD),
            f("batchId", "配送批次", "BigDecimal", Section.HEAD),
            f("colBlockNo", "列块序号", "BigDecimal", Section.HEAD),
            f("totalColBlocks", "列块总数", "BigDecimal", Section.HEAD),
            f("colBlockLabel", "列块标签", "String", Section.HEAD),
            f("totalQuantity", "合计数量", "BigDecimal", Section.HEAD),
            f("totalKinds", "品项数", "BigDecimal", Section.HEAD),
            f("pointCount", "配送点数", "BigDecimal", Section.HEAD),
            f("layoutVersion", "布局快照版本", "BigDecimal", Section.HEAD),
            f("layoutDerived", "布局实时推导", "Boolean", Section.HEAD),
            f("identityOk", "恒等式自检", "Boolean", Section.HEAD),
            f("warnings", "数据告警", "Object", Section.HEAD),
            f("printTime", "打印时间", "String", Section.HEAD),
            f("remark", "备注", "String", Section.HEAD),
            // 槽位列名（宽表用）：c1Name..c{colsPerPage}
            f("c1Name", "第1列配送点", "String", Section.HEAD),
            f("c2Name", "第2列配送点", "String", Section.HEAD),
            f("c3Name", "第3列配送点", "String", Section.HEAD),
            f("c4Name", "第4列配送点", "String", Section.HEAD),
            f("c5Name", "第5列配送点", "String", Section.HEAD),
            f("c6Name", "第6列配送点", "String", Section.HEAD),
            f("c7Name", "第7列配送点", "String", Section.HEAD));

    private static final List<Field> MATRIX_COLUMNS = List.of(
            f("seq", "列序", "BigDecimal", Section.COLUMNS),
            f("deptSeq", "列序(长表)", "BigDecimal", Section.COLUMNS),
            f("deptId", "配送点ID", "BigDecimal", Section.COLUMNS),
            f("deptCode", "配送点编码", "String", Section.COLUMNS),
            f("deptName", "配送点名称", "String", Section.COLUMNS),
            f("deptLabel", "列头显示名(01·点心)", "String", Section.COLUMNS),
            f("adHoc", "临时补列", "Boolean", Section.COLUMNS),
            f("hasData", "当日有单", "Boolean", Section.COLUMNS),
            f("empty", "套打占位空列", "Boolean", Section.COLUMNS));

    private static final List<Field> MATRIX_ROWS_WIDE = List.of(
            f("seq", "行号", "BigDecimal", Section.ROWS),
            f("productName", "品名", "String", Section.ROWS),
            f("productSpec", "规格", "String", Section.ROWS),
            f("productUnit", "单位", "String", Section.ROWS),
            f("remark", "备注(档位)", "String", Section.ROWS),
            f("c1", "第1列数量", "BigDecimal", Section.ROWS),
            f("c2", "第2列数量", "BigDecimal", Section.ROWS),
            f("c3", "第3列数量", "BigDecimal", Section.ROWS),
            f("c4", "第4列数量", "BigDecimal", Section.ROWS),
            f("c5", "第5列数量", "BigDecimal", Section.ROWS),
            f("c6", "第6列数量", "BigDecimal", Section.ROWS),
            f("c7", "第7列数量", "BigDecimal", Section.ROWS),
            f("total", "行合计", "BigDecimal", Section.ROWS));

    private static final List<Field> MATRIX_ROWS_LONG = List.of(
            f("seq", "菜品行序", "BigDecimal", Section.ROWS),
            f("rowKey", "菜品行键", "String", Section.ROWS),
            f("productName", "品名", "String", Section.ROWS),
            f("productSpec", "规格", "String", Section.ROWS),
            f("productUnit", "单位", "String", Section.ROWS),
            f("remark", "备注(档位)", "String", Section.ROWS),
            f("deptSeq", "配送点列序", "BigDecimal", Section.ROWS),
            f("deptId", "配送点ID", "BigDecimal", Section.ROWS),
            f("deptName", "配送点名称", "String", Section.ROWS),
            f("deptLabel", "列头显示名", "String", Section.ROWS),
            f("deptCode", "配送点编码", "String", Section.ROWS),
            f("shiftCode", "班次编码", "String", Section.ROWS),
            f("num", "数量(null=空格)", "BigDecimal", Section.ROWS),
            f("rowTotal", "菜品行合计", "BigDecimal", Section.ROWS));

    private static final List<Field> FLAT_HEAD = List.of(
            f("printTitle", "打印标题", "String", Section.HEAD),
            f("code", "单号", "String", Section.HEAD),
            f("bizKey", "打印主体键", "String", Section.HEAD),
            f("customerId", "客户ID", "BigDecimal", Section.HEAD),
            f("customerDeptId", "配送点ID", "BigDecimal", Section.HEAD),
            f("customerName", "客户名称", "String", Section.HEAD),
            f("deliveryPointName", "配送点名称", "String", Section.HEAD),
            f("deliveryDate", "配送日期", "String", Section.HEAD),
            f("totalNum", "应送合计", "BigDecimal", Section.HEAD),
            f("totalAmount", "金额合计", "BigDecimal", Section.HEAD),
            f("printTime", "打印时间", "String", Section.HEAD),
            f("warnings", "数据告警", "Object", Section.HEAD));

    private static final List<Field> FLAT_ROWS = List.of(
            f("seq", "行号", "BigDecimal", Section.ROWS),
            f("productName", "品名", "String", Section.ROWS),
            f("productSpec", "规格", "String", Section.ROWS),
            f("productUnit", "单位", "String", Section.ROWS),
            f("num", "应送数量", "BigDecimal", Section.ROWS),
            f("price", "单价", "BigDecimal", Section.ROWS),
            f("amount", "金额", "BigDecimal", Section.ROWS),
            f("acceptanceNum", "验收数(留空手填)", "String", Section.ROWS),
            f("changeTag", "变更标记(加单/换货/退货)", "String", Section.ROWS));

    // ==================== 查询 API ====================

    /** 某形态/行形态的全部字段 */
    public static List<Field> fields(Form form, RowsShape shape) {
        if (form == Form.FLAT) {
            return concat(FLAT_HEAD, FLAT_ROWS);
        }
        List<Field> rows = shape == RowsShape.WIDE ? MATRIX_ROWS_WIDE : MATRIX_ROWS_LONG;
        return concat(concat(MATRIX_HEAD, MATRIX_COLUMNS), rows);
    }

    /** 按数据集编码取字段（物化/字段面板按数据集分组展示用） */
    public static List<Field> fieldsOfDataSet(String dbCode, RowsShape shape) {
        return switch (dbCode == null ? "" : dbCode) {
            case "hd" -> FLAT_HEAD;
            case "dd" -> FLAT_ROWS;
            case "hm" -> MATRIX_HEAD;
            case "hc" -> MATRIX_COLUMNS;
            case "dc" -> MATRIX_ROWS_LONG;
            case "dm" -> MATRIX_ROWS_WIDE;
            default -> List.of();
        };
    }

    /** 形态的数据集参数（URL 同名透传） */
    public static List<Param> params(Form form) {
        if (form == Form.FLAT) {
            return List.of(
                    p("deliveryOrderId", "送货单ID", ""),
                    p("customerId", "客户ID", ""),
                    p("customerDeptId", "配送点ID", ""),
                    p("deliveryDate", "配送日期", ""),
                    p("ticket", "打印票据", ""));
        }
        return List.of(
                p("deliveryOrderId", "送货单ID", ""),
                p("customerId", "客户ID", ""),
                p("deliveryDate", "配送日期", ""),
                p("colBlock", "列块序号", "1"),
                p("emptyCols", "空列策略(skip/keep)", "skip"),
                p("ticket", "打印票据", ""));
    }

    /** 形态/行形态的数据集清单 */
    public static List<DataSet> dataSets(Form form, RowsShape shape) {
        if (form == Form.FLAT) {
            return List.of(
                    new DataSet("hd", "送货单表头", false, "deliveryDataConvertAdapter",
                            "/print/deliveryHead", null),
                    new DataSet("dd", "送货单明细", true, "deliveryDataConvertAdapter",
                            "/print/deliveryData", null));
        }
        if (shape == RowsShape.WIDE) {
            return List.of(
                    new DataSet("hm", "总单表头", false, "deliveryDataConvertAdapter",
                            "/print/deliveryMatrixData", null),
                    new DataSet("dm", "总单明细(矩阵行)", true, "deliveryRowsConvertAdapter",
                            "/print/deliveryMatrixData", RowsShape.WIDE));
        }
        return List.of(
                new DataSet("hm", "总单表头", false, "deliveryDataConvertAdapter",
                        "/print/deliveryMatrixData", null),
                new DataSet("hc", "总单列定义（横向分组表头）", true, "deliveryMatrixColumnsConvertAdapter",
                        "/print/deliveryMatrixData", null),
                new DataSet("dc", "总单明细（全交叉长表）", true, "deliveryRowsConvertAdapter",
                        "/print/deliveryMatrixData", RowsShape.LONG));
    }

    /**
     * 数据集的 URL 查询串（{@code ${param}} 占位，由报表视图 URL 同名透传）。
     * 长表附带 {@code rowsType=long}；宽表走端点默认（wide）。
     */
    public static String apiQuery(Form form, RowsShape shape) {
        if (form == Form.FLAT) {
            return "?deliveryOrderId=${deliveryOrderId}&customerId=${customerId}"
                    + "&customerDeptId=${customerDeptId}&deliveryDate=${deliveryDate}&ticket=${ticket}";
        }
        String rowsType = shape == RowsShape.LONG ? "&rowsType=long&emptyCols=${emptyCols}" : "";
        return "?deliveryOrderId=${deliveryOrderId}&customerId=${customerId}"
                + "&deliveryDate=${deliveryDate}&colBlock=${colBlock}" + rowsType + "&ticket=${ticket}";
    }

    /** 字符串 → 形态（容错，缺省 FLAT） */
    public static Form formOf(String value) {
        return "MATRIX".equalsIgnoreCase(value) ? Form.MATRIX : Form.FLAT;
    }

    /** 字符串 → 行形态（容错，缺省 LONG=通用推荐） */
    public static RowsShape shapeOf(String value) {
        return "WIDE".equalsIgnoreCase(value) ? RowsShape.WIDE : RowsShape.LONG;
    }

    // ==================== 内部 ====================

    private static Field f(String code, String text, String type, Section section) {
        return new Field(code, text, type, section);
    }

    private static Param p(String code, String text, String defaultValue) {
        return new Param(code, text, defaultValue);
    }

    private static List<Field> concat(List<Field> a, List<Field> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }

    private PrintDataContract() {
    }
}

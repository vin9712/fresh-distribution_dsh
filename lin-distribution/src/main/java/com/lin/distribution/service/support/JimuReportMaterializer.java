package com.lin.distribution.service.support;

import com.lin.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JimuReport 报表物化器（PR-D1/D2/D3）
 *
 * <p>职责：把「打印模板」变成 JimuReport 里可渲染、可复现的报表产物，并让
 * {@code jimu_report*} 从「手工维护的事实来源」降级为「由本类生成的产物」。</p>
 *
 * <ul>
 *   <li>{@link #resolveDesign(String)}：容错读取设计 JSON（content 可能是报表ID 或设计 JSON）；</li>
 *   <li>{@link #cloneReport(String, String, String, String)}：以指定设计 JSON 克隆报表（含数据集/字段/参数）；</li>
 *   <li>{@link #createReport(String, String, String)}：无参照报表时按设计 JSON 裸建；</li>
 *   <li>{@link #ensureMaterialized(String, PrintDataContract.Form)}：<b>契约驱动接线对齐</b>——
 *       按 {@link PrintDataContract} 对齐数据集 URL/转换器/参数，并补齐打印回执钩子。
 *       修 PR-A4（数据集靠 SQL 手工维护、URL 硬编码 localhost）与「酒店动态模板漏挂回执钩子」。</li>
 * </ul>
 *
 * @author dsh
 */
@Slf4j
@Component
public class JimuReportMaterializer {

    /** 默认取数基址（JimuReport 服务端回调本系统）；生产以 lin.print.api-base-url 覆盖 */
    public static final String DEFAULT_API_BASE_URL = "http://localhost:8090";
    /** 默认打印回执钩子版本（print-annotation.js?v=N） */
    public static final String DEFAULT_ANNOTATION_VERSION = "11";
    /** 打印回执钩子模板（真实打印后回传 /print/receipt 登记打印分界，PT-3） */
    private static final String ANNOTATION_HOOK =
            "(function(){var s=document.createElement(\"script\");s.src=\"/jmreport/desreport_/ext/"
                    + "print-annotation.js?v=%s\";document.head.appendChild(s);})()";

    private final JdbcTemplate jdbc;

    /** 取数基址（可配置，修 PR-A4 的 localhost 硬编码） */
    @Value("${lin.print.api-base-url:" + DEFAULT_API_BASE_URL + "}")
    private String apiBaseUrl;

    /** 打印回执钩子版本（可配置，替代散落 SQL 的 v=10/v=11） */
    @Value("${lin.print.annotation-version:" + DEFAULT_ANNOTATION_VERSION + "}")
    private String annotationVersion;

    public JimuReportMaterializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.apiBaseUrl = DEFAULT_API_BASE_URL;
        this.annotationVersion = DEFAULT_ANNOTATION_VERSION;
    }

    // ==================== 设计 JSON 读取/克隆 ====================

    /**
     * 容错解析设计 JSON：content 为 {@code {} 开头} 视为设计 JSON，否则视为报表ID去查 json_str。
     */
    public String resolveDesign(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        return selectDesign(trimmed);
    }

    /** 读取报表设计 JSON（jimu_report.json_str） */
    public String selectDesign(String reportId) {
        if (StringUtils.isBlank(reportId)) {
            return null;
        }
        List<String> rows = jdbc.queryForList("select json_str from jimu_report where id = ?", String.class, reportId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 以指定设计 JSON 克隆一张报表（连同数据集/字段/参数），返回新报表ID。
     *
     * @param sourceReportId 参照报表ID（数据集/字段/参数来源；可为 null=无接线，仅建设计）
     * @param designJson     新报表的设计 JSON；为 null 时沿用参照报表设计
     * @param name           新报表名称（可为 null）
     * @param code           新报表编码（可为 null，缺省自动生成）
     * @return 新报表ID
     */
    public String cloneReport(String sourceReportId, String designJson, String name, String code) {
        if (StringUtils.isBlank(sourceReportId)) {
            return createReport(designJson, name, code);
        }
        Map<String, Object> report = queryOne("select * from jimu_report where id = ?", sourceReportId);
        if (report == null) {
            throw new ServiceException("参照报表不存在，无法物化：" + sourceReportId);
        }
        String newId = newId();
        report.put("id", newId);
        report.put("code", StringUtils.defaultIfBlank(code, reportCode(newId)));
        if (StringUtils.isNotBlank(name)) {
            report.put("name", truncate(name, 50));
        }
        if (designJson != null) {
            report.put("json_str", designJson);
        }
        report.put("create_time", new Date());
        report.put("update_time", null);
        insertRow("jimu_report", report);

        List<Map<String, Object>> dataSets = jdbc.queryForList(
                "select * from jimu_report_db where jimu_report_id = ?", sourceReportId);
        for (Map<String, Object> ds : dataSets) {
            String oldDsId = str(ds.get("id"));
            String newDsId = newId();
            ds.put("id", newDsId);
            ds.put("jimu_report_id", newId);
            insertRow("jimu_report_db", ds);
            cloneChildren("jimu_report_db_field", "jimu_report_db_id", oldDsId, newDsId);
            cloneChildren("jimu_report_db_param", "jimu_report_head_id", oldDsId, newDsId);
        }
        log.info("[jimu-materialize] 克隆报表 {} -> {}（数据集 {} 套）", sourceReportId, newId, dataSets.size());
        return newId;
    }

    /** 按设计 JSON 裸建一张报表（无参照报表时使用；接线由 ensureMaterialized 补齐） */
    public String createReport(String designJson, String name, String code) {
        if (StringUtils.isBlank(designJson)) {
            throw new ServiceException("设计 JSON 不能为空，无法物化报表");
        }
        String newId = newId();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", newId);
        report.put("code", StringUtils.defaultIfBlank(code, reportCode(newId)));
        report.put("name", truncate(StringUtils.defaultIfBlank(name, "打印模板"), 50));
        report.put("note", "由打印模板物化生成");
        report.put("type", "0");
        report.put("json_str", designJson);
        report.put("create_by", "system");
        report.put("create_time", new Date());
        report.put("del_flag", 0);
        report.put("template", 0);
        report.put("view_count", 0L);
        report.put("update_count", 0);
        report.put("tenant_id", "1");
        insertRow("jimu_report", report);
        log.info("[jimu-materialize] 新建报表 {}（接线待补）", newId);
        return newId;
    }

    // ==================== 契约驱动物化（PR-D3） ====================

    /**
     * 按数据契约对齐报表接线（幂等）：
     * <ol>
     *   <li>探测行形态（有 dc→LONG，有 dm→WIDE，空报表→LONG 通用推荐）；</li>
     *   <li>对齐该形态的数据集：存在则更新 URL/转换器/名称，缺失则插入（空报表补齐）；</li>
     *   <li>补齐数据集参数（search_flag=0，URL 同名透传）；</li>
     *   <li>补齐/升级打印回执钩子（print-annotation.js?v=N）。</li>
     * </ol>
     *
     * @param reportId 报表ID
     * @param form     打印形态
     * @return 物化结果摘要
     */
    @Transactional
    public MaterializeResult ensureMaterialized(String reportId, PrintDataContract.Form form) {
        if (StringUtils.isBlank(reportId)) {
            throw new ServiceException("报表ID不能为空，无法物化");
        }
        if (selectDesign(reportId) == null) {
            throw new ServiceException("报表不存在，无法物化：" + reportId);
        }
        PrintDataContract.RowsShape shape = detectShape(reportId, form);

        Map<String, String> idByCode = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "select id, db_code from jimu_report_db where jimu_report_id = ?", reportId)) {
            idByCode.put(str(row.get("db_code")), str(row.get("id")));
        }

        int inserted = 0;
        int updated = 0;
        int params = 0;
        for (PrintDataContract.DataSet ds : PrintDataContract.dataSets(form, shape)) {
            String apiUrl = apiBaseUrl + ds.endpoint() + PrintDataContract.apiQuery(form, shape);
            String dsId = idByCode.get(ds.dbCode());
            if (dsId == null) {
                dsId = newId();
                insertRow("jimu_report_db", datasetRow(dsId, reportId, ds, apiUrl));
                inserted++;
            } else {
                jdbc.update("update jimu_report_db set api_url = ?, api_convert = ?, db_ch_name = ?, is_list = ?, "
                                + "update_time = now() where id = ?",
                        apiUrl, ds.apiConvert(), ds.chName(), ds.list() ? "1" : "0", dsId);
                updated++;
            }
            params += ensureParams(dsId, form);
        }

        boolean hook = ensureAnnotationHook(reportId);
        MaterializeResult result = new MaterializeResult(reportId, form.name(), shape.name(),
                inserted, updated, params, hook);
        log.info("[jimu-materialize] 物化 {} → {}", reportId, result);
        return result;
    }

    /** 探测行形态：优先按已有数据集判断，空报表用 LONG（通用推荐，配送点增减模板零改动） */
    public PrintDataContract.RowsShape detectShape(String reportId, PrintDataContract.Form form) {
        if (form != PrintDataContract.Form.MATRIX) {
            return PrintDataContract.RowsShape.LONG;
        }
        List<String> codes = jdbc.queryForList(
                "select db_code from jimu_report_db where jimu_report_id = ?", String.class, reportId);
        if (codes.contains("dc")) {
            return PrintDataContract.RowsShape.LONG;
        }
        if (codes.contains("dm")) {
            return PrintDataContract.RowsShape.WIDE;
        }
        return PrintDataContract.RowsShape.LONG;
    }

    /** 补齐数据集参数（幂等：按 head_id + param_name 不存在才插） */
    private int ensureParams(String dataSetId, PrintDataContract.Form form) {
        int count = 0;
        int order = 0;
        for (PrintDataContract.Param param : PrintDataContract.params(form)) {
            order++;
            count += jdbc.update(
                    "insert into jimu_report_db_param "
                            + "(id, jimu_report_head_id, param_name, param_txt, param_value, order_num, "
                            + " create_by, create_time, search_flag, dict_code, ext_json) "
                            + "select ?, ?, ?, ?, ?, ?, 'system', now(), 0, '', '' "
                            + "where not exists (select 1 from jimu_report_db_param "
                            + " where jimu_report_head_id = ? and param_name = ?)",
                    newId(), dataSetId, param.code(), param.text(),
                    StringUtils.defaultString(param.defaultValue()), order, dataSetId, param.code());
        }
        return count;
    }

    /**
     * 补齐/升级打印回执钩子（PT-3）：空则写入；已有其它 js 则追加；版本落后则原地替换版本号。
     *
     * @return 是否发生变更
     */
    private boolean ensureAnnotationHook(String reportId) {
        List<String> rows = jdbc.queryForList("select js_str from jimu_report where id = ?", String.class, reportId);
        if (rows.isEmpty()) {
            return false;
        }
        String current = rows.get(0);
        String hook = String.format(ANNOTATION_HOOK, annotationVersion);
        String next;
        if (StringUtils.isBlank(current)) {
            next = hook;
        } else if (current.contains("print-annotation.js")) {
            next = current.replaceAll("print-annotation\\.js\\?v=\\d+",
                    "print-annotation.js?v=" + annotationVersion);
            if (next.equals(current)) {
                return false;
            }
        } else {
            next = current + hook;
        }
        jdbc.update("update jimu_report set js_str = ?, update_time = now() where id = ?", next, reportId);
        return true;
    }

    private Map<String, Object> datasetRow(String dsId, String reportId, PrintDataContract.DataSet ds, String apiUrl) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", dsId);
        row.put("jimu_report_id", reportId);
        row.put("create_by", "system");
        row.put("create_time", new Date());
        row.put("db_code", ds.dbCode());
        row.put("db_ch_name", ds.chName());
        row.put("db_type", "1");
        row.put("api_url", apiUrl);
        row.put("api_method", "0");
        row.put("is_list", ds.list() ? "1" : "0");
        row.put("is_page", "0");
        row.put("api_convert", ds.apiConvert());
        return row;
    }

    // ==================== 内部工具 ====================

    private void cloneChildren(String table, String fkColumn, String oldFk, String newFk) {
        List<Map<String, Object>> children = jdbc.queryForList(
                "select * from " + table + " where " + fkColumn + " = ?", oldFk);
        for (Map<String, Object> child : children) {
            child.put("id", newId());
            child.put(fkColumn, newFk);
            insertRow(table, child);
        }
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : new LinkedHashMap<>(rows.get(0));
    }

    /** 通用单行插入：列名动态取自行键 */
    private void insertRow(String table, Map<String, Object> row) {
        List<String> columns = new ArrayList<>(row.keySet());
        String columnSql = String.join(",", columns.stream().map(c -> "`" + c + "`").toList());
        String placeholderSql = String.join(",", Collections.nCopies(columns.size(), "?"));
        Object[] values = columns.stream().map(row::get).toArray();
        jdbc.update("insert into " + table + " (" + columnSql + ") values (" + placeholderSql + ")", values);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String reportCode(String id) {
        return "PT" + id;
    }

    /** 新报表ID：19 位数字字符串（jimu_report.id varchar(32)） */
    private static String newId() {
        return String.valueOf(System.currentTimeMillis())
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    /** 物化结果摘要（接口回显） */
    public record MaterializeResult(String reportId, String form, String rowsType,
                                    int dataSetInserted, int dataSetUpdated, int paramInserted,
                                    boolean annotationHookUpdated) {
    }
}

package com.lin.distribution.service.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lin.common.exception.ServiceException;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.mapper.PrintTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 导入静态样张创建配置。报表/数据集/字段/业务草稿在同一事务内创建，不覆盖既有报表。 */
@Service
@RequiredArgsConstructor
public class JimuSampleImporter {
    private final JdbcTemplate jdbc;
    private final PrintTemplateMapper templates;
    private final TemplateContentGovernor governor;
    private static final Pattern FIELD = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,31}");
    private static final Pattern REF = Pattern.compile("#\\{([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)}");
    /** 源样张公式是表格软件语法，不是积木表达式：IF(#{d.f}==0,'',#{d.f}) / color(…) / 纯求和。 */
    private static final Pattern IF_ZERO = Pattern.compile(
            "^\\s*=\\s*IF\\(\\s*#\\{(\\w+)\\.(\\w+)}\\s*==\\s*0\\s*,\\s*''\\s*,\\s*#\\{\\1\\.(\\w+)}\\s*\\)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR = Pattern.compile(
            "^\\s*=\\s*color\\(\\s*'?\\s*(#\\{\\w+\\.\\w+})\\s*'?\\s*,.*\\)\\s*$", Pattern.CASE_INSENSITIVE);
    /** 判零包裹取色的源样张写法（数量列）。取色需舍弃：报表引擎的 color() 对空值输出字面量 null，
     *  而 color() 又必须是最外层调用（用 IF/三元包裹会静默失败），两者无法共存，此处以留白为准。 */
    private static final Pattern IF_COLOR = Pattern.compile(
            "^\\s*=\\s*IF\\(\\s*#\\{(\\w+)\\.(\\w+)}\\s*==\\s*0\\s*,\\s*''\\s*,\\s*(color\\(.*\\))\\s*\\)\\s*$",
            Pattern.CASE_INSENSITIVE);
    /** 取色包裹多引用求和（小计列）：同上舍弃取色，合计预计算成字段。 */
    private static final Pattern COLOR_SUM = Pattern.compile(
            "^\\s*=\\s*color\\(\\s*(#\\{\\w+\\.\\w+}(?:\\s*\\+\\s*#\\{\\w+\\.\\w+})+)\\s*(,\\s*.*?)?\\)\\s*$",
            Pattern.CASE_INSENSITIVE);
    /** 表格软件的列合计 =SUM(D4)：字母为列、数字为明细行，按该列绑定字段在静态数据上预计算。 */
    private static final Pattern COL_SUM = Pattern.compile(
            "^\\s*=\\s*SUM\\(\\s*([A-Za-z]{1,3})([0-9]+)\\s*\\)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUM = Pattern.compile(
            "^\\s*=\\s*(#\\{\\w+\\.\\w+}(?:\\s*\\+\\s*#\\{\\w+\\.\\w+})+)\\s*$");

    @Transactional
    public int importSample(String source) {
        JSONObject original = validate(source);
        JSONObject config = normalize(original);
        String reportId = id();
        String operator = SecurityUtils.getUsername();
        JSONObject sheet = sheet(config, reportId);
        governor.validateImport(sheet);
        String name = config.getString("reportName") + "（静态样板）";
        jdbc.update("insert into jimu_report (id,code,name,type,json_str,create_by,create_time,del_flag,template,tenant_id) "
                        + "values (?,?,?,'0',?,?,now(),0,0,'1')",
                reportId, "sample_" + reportId, name, sheet.toJSONString(), operator);
        // 字段类型取自归一化前的原始样张：normalize 会把数值统一转成显示字符串，
        // 归一化后 instanceof Number 恒为 false，数字列会全部误落 String（影响报表内排序/格式化）
        Map<String, Object> rawFirstRows = new HashMap<>();
        for (Object item : original.getJSONArray("datasets")) {
            JSONObject ds = (JSONObject) item;
            rawFirstRows.put(ds.getString("dbCode"), ds.getJSONArray("jsonData").getJSONObject(0));
        }
        for (Object item : config.getJSONArray("datasets")) {
            JSONObject ds = (JSONObject) item;
            String dbCode = ds.getString("dbCode");
            Object rawFirst = rawFirstRows.get(dbCode);
            String dsId = id();
            JSONObject data = new JSONObject();
            data.put("data", ds.getJSONArray("jsonData"));
            jdbc.update("insert into jimu_report_db (id,jimu_report_id,db_code,db_ch_name,db_type,is_list,is_page,json_data,create_by,create_time) "
                            + "values (?,?,?,?,'3','1','0',?,?,now())",
                    dsId, reportId, dbCode, ds.getString("dbChName"), data.toJSONString(), operator);
            int order = 0;
            for (Object f : ds.getJSONArray("fieldList")) {
                JSONArray field = (JSONArray) f;
                String fieldName = field.getString(0);
                Object example = rawFirst instanceof JSONObject ? ((JSONObject) rawFirst).get(fieldName) : null;
                // normalize 新增的合计字段（sum*）恒为数值
                String widget = example instanceof Number || fieldName.startsWith("sum") ? "BigDecimal" : "String";
                jdbc.update("insert into jimu_report_db_field (id,jimu_report_db_id,field_name,field_text,widget_type,order_num,search_flag,create_by,create_time) "
                                + "values (?,?,?,?,?,?,0,?,now())",
                        id(), dsId, fieldName, field.getString(1), widget, ++order, operator);
            }
        }
        PrintTemplate template = new PrintTemplate();
        template.setCode("SAMPLE_" + reportId);
        template.setName(name);
        template.setContent(reportId); // 打印链路的 content 契约是报表 ID，而非设计 JSON
        template.setType(0);
        template.setRenderEngine("jimureport");
        template.setPrintForm("MATRIX");
        template.setBindType(3);
        template.setCustomerId(0L);
        template.setCopies(1);
        template.setIsDefault("0");
        template.setStatus(0);
        template.setIsDeleted(false);
        template.setTestWatermark(true);
        template.setCreateBy(operator);
        template.setCreateTime(new Date());
        template.setRemark("静态样例数据，仅用于版式对照；未接真实订单，不可用于正式配送。");
        return templates.insertPrintTemplate(template);
    }

    /** 先完成所有结构校验，再写入任何一张表。 */
    public JSONObject validate(String source) {
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > TemplateContentGovernor.IMPORT_MAX_BYTES) {
            throw new ServiceException("样板文件为空或超过20MB");
        }
        JSONObject c;
        try { c = JSON.parseObject(source); }
        catch (Exception e) { throw new ServiceException("样板不是合法 JSON 对象"); }
        require(c != null && "create".equals(c.getString("action")), "仅支持 create 样板配置");
        String name = c.getString("reportName");
        require(name != null && !name.isBlank() && name.length() <= 35, "样板名称必填且不超过35字");
        require(c.getJSONObject("customRows") != null && c.getJSONObject("customCols") != null
                && c.getJSONArray("customStyles") != null && c.getJSONArray("customMerges") != null, "样板缺少行列、样式或合并定义");
        JSONArray datasets = c.getJSONArray("datasets");
        require(datasets != null && !datasets.isEmpty(), "样板缺少数据集");
        Set<String> codes = new HashSet<>();
        Set<String> refs = new HashSet<>();
        for (Object item : datasets) {
            require(item instanceof JSONObject, "数据集必须为对象");
            JSONObject ds = (JSONObject) item;
            String code = ds.getString("dbCode");
            require(code != null && FIELD.matcher(code).matches() && codes.add(code), "数据集编码非法或重复");
            require("3".equals(ds.getString("dbType")) && "1".equals(ds.getString("isList"))
                    && "0".equals(ds.getString("isPage")), "样板导入仅支持不分页静态列表，不接收SQL或API数据源");
            JSONArray fields = ds.getJSONArray("fieldList");
            JSONArray data = ds.getJSONArray("jsonData");
            require(fields != null && !fields.isEmpty() && data != null && !data.isEmpty(), "字段和静态数据不能为空");
            Set<String> names = new HashSet<>();
            for (Object f : fields) {
                require(f instanceof JSONArray && ((JSONArray) f).size() == 2, "字段必须为[编码,名称]");
                String key = ((JSONArray) f).getString(0);
                require(key != null && FIELD.matcher(key).matches() && names.add(key), "字段编码非法或重复");
                refs.add(code + "." + key);
            }
            for (Object row : data) {
                require(row instanceof JSONObject && ((JSONObject) row).keySet().containsAll(names), "静态明细缺少声明字段");
            }
        }
        JSONObject rows = c.getJSONObject("customRows");
        for (String key : rows.keySet()) {
            if ("len".equals(key)) continue;
            require(key.matches("[0-9]+"), "行号非法");
            JSONObject cells = rows.getJSONObject(key).getJSONObject("cells");
            if (cells == null) continue;
            for (String col : cells.keySet()) {
                require(col.matches("[0-9]+"), "列号非法");
                JSONObject cell = cells.getJSONObject(col);
                if (cell.containsKey("style")) {
                    Integer style = cell.getInteger("style");
                    require(style != null && style >= 0 && style < c.getJSONArray("customStyles").size(), "样式索引越界");
                }
                String text = cell.getString("text");
                if (text == null) continue;
                Matcher matcher = REF.matcher(text);
                while (matcher.find()) require(refs.contains(matcher.group(1) + "." + matcher.group(2)), "单元格引用未声明字段");
            }
        }
        return c;
    }

    public JSONObject sheet(JSONObject c, String reportId) {
        JSONObject s = JSON.parseObject("{\"schemaVersion\":1,\"name\":\"sheet1\",\"loopBlockList\":[],"
                + "\"querySetting\":{\"izOpenQueryBar\":false,\"izDefaultQuery\":true},"
                + "\"recordSubTableOrCollection\":{\"group\":[],\"record\":[],\"range\":[]},"
                + "\"printConfig\":{\"paper\":\"A4\",\"width\":210,\"height\":297,\"layout\":\"portrait\",\"definition\":1,\"isBackend\":false,\"marginX\":8,\"marginY\":10,\"watermarkShow\":true,\"watermarkText\":\"静态样板·非正式单据\"},"
                + "\"hidden\":{\"rows\":[],\"cols\":[]},\"queryFormSetting\":{\"useQueryForm\":false,\"dbKey\":\"\",\"idField\":\"\"},"
                + "\"dbexps\":[],\"dicts\":[],\"freeze\":\"A1\",\"autofilter\":{},\"validations\":[],\"submitHandlers\":[],"
                + "\"hiddenCells\":[],\"zonedEditionList\":[],\"rpbar\":{\"show\":true,\"pageSize\":\"\",\"btnList\":[]},"
                + "\"fixedPrintHeadRows\":[],\"fixedPrintTailRows\":[],\"displayConfig\":{},\"background\":false,\"isViewContentHorizontalCenter\":false}");
        s.put("rows", c.getJSONObject("customRows"));
        s.put("cols", c.getJSONObject("customCols"));
        s.put("styles", c.getJSONArray("customStyles"));
        s.put("merges", c.getJSONArray("customMerges"));
        s.put("excel_config_id", reportId);
        int width = 0;
        for (String key : c.getJSONObject("customCols").keySet()) {
            if (!"len".equals(key)) width += c.getJSONObject("customCols").getJSONObject(key).getIntValue("width");
        }
        s.put("dataRectWidth", width);
        s.put("area", JSON.parseObject("{\"sri\":0,\"sci\":0,\"eri\":0,\"eci\":0,\"width\":100,\"height\":25}"));
        return s;
    }

    /**
     * 静态样张归一化：IF 判零改为绑定，求和预计算；COLOR 转为积木支持的小写 color。
     * 数值统一为显示字符串，0 写成空串以替代本样张的 IF 判零隐藏。
     * 此转换仅面向静态样板，不作为真实订单数据契约。
     */
    public JSONObject normalize(JSONObject source) {
        JSONObject c = JSON.parseObject(source.toJSONString());
        JSONObject rows = c.getJSONObject("customRows");
        // 第一遍：列合计 =SUM(D4) 必须在公式改写前解析，否则取不到明细行绑定的原始字段
        for (String key : rows.keySet()) {
            if ("len".equals(key)) continue;
            JSONObject cells = rows.getJSONObject(key).getJSONObject("cells");
            if (cells == null) continue;
            for (String col : cells.keySet()) {
                JSONObject cell = cells.getJSONObject(col);
                String text = cell.getString("text");
                if (text == null) continue;
                Matcher colSum = COL_SUM.matcher(text);
                if (colSum.matches()) {
                    cell.put("text", columnTotal(c, colSum.group(1), colSum.group(2)));
                }
            }
        }
        // 第二遍：公式归一化为积木表达式
        Map<String, List<String>> sums = new LinkedHashMap<>();
        for (String key : rows.keySet()) {
            if ("len".equals(key)) continue;
            JSONObject cells = rows.getJSONObject(key).getJSONObject("cells");
            if (cells == null) continue;
            for (String col : cells.keySet()) {
                JSONObject cell = cells.getJSONObject(col);
                String text = cell.getString("text");
                if (text == null) continue;
                Matcher zero = IF_ZERO.matcher(text);
                if (zero.matches()) {
                    cell.put("text", "#{" + zero.group(1) + "." + zero.group(3) + "}");
                    continue;
                }
                Matcher ifColor = IF_COLOR.matcher(text);
                if (ifColor.matches()) {
                    // 去掉判零外壳与取色。只看 color() 首个逗号前的首参（求和表达式）：
                    // 单引用 → 纯绑定；多引用求和 → 预计算为合计字段，避免只绑首引用丢量。
                    // 后续参数（颜色字段引用/字面量）不参与判定，否则颜色字段会被误当数据求和
                    String colorCall = ifColor.group(3);
                    int firstComma = colorCall.indexOf(',');
                    String firstArg = firstComma < 0 ? colorCall : colorCall.substring(0, firstComma);
                    List<String> fields = new ArrayList<>();
                    Matcher inner = REF.matcher(firstArg);
                    String code = null;
                    while (inner.find()) {
                        code = inner.group(1);
                        fields.add(inner.group(2));
                    }
                    if (code != null) {
                        if (fields.size() == 1) {
                            cell.put("text", "#{" + code + "." + fields.get(0) + "}");
                        } else {
                            String sumKey = "sum" + (sums.size() + 1);
                            sums.put(code + "." + sumKey, fields);
                            cell.put("text", "#{" + code + "." + sumKey + "}");
                        }
                    }
                    continue;
                }
                Matcher colorSum = COLOR_SUM.matcher(text);
                if (colorSum.matches()) {
                    // 小计列：舍弃取色，合计预计算为字段，同时避开空值算术在报表引擎里报错
                    List<String> fields = new ArrayList<>();
                    Matcher colorRefs = REF.matcher(colorSum.group(1));
                    String code = null;
                    while (colorRefs.find()) {
                        code = colorRefs.group(1);
                        fields.add(colorRefs.group(2));
                    }
                    String sumKey = "sum" + (sums.size() + 1);
                    sums.put(code + "." + sumKey, fields);
                    cell.put("text", "#{" + code + "." + sumKey + "}");
                    continue;
                }
                Matcher color = COLOR.matcher(text);
                if (color.matches()) {
                    cell.put("text", text.replaceFirst("(?i)^\\s*=\\s*color", "=color"));
                    continue;
                }
                Matcher sum = SUM.matcher(text);
                if (sum.matches()) {
                    List<String> fields = new ArrayList<>();
                    Matcher refs = REF.matcher(sum.group(1));
                    String code = null;
                    while (refs.find()) {
                        code = refs.group(1);
                        fields.add(refs.group(2));
                    }
                    String sumKey = "sum" + (sums.size() + 1);
                    sums.put(code + "." + sumKey, fields);
                    cell.put("text", "#{" + code + "." + sumKey + "}");
                }
            }
        }
        // 第三遍：把预计算的合计字段并入静态数据（含 0→空串归一化）
        for (Object item : c.getJSONArray("datasets")) {
            JSONObject ds = (JSONObject) item;
            String code = ds.getString("dbCode");
            Set<String> names = new LinkedHashSet<>();
            for (Object f : ds.getJSONArray("fieldList")) names.add(((JSONArray) f).getString(0));
            List<String> declared = new ArrayList<>(names);
            for (Map.Entry<String, List<String>> sum : sums.entrySet()) {
                if (!sum.getKey().startsWith(code + ".")) continue;
                String sumKey = sum.getKey().substring(code.length() + 1);
                ds.getJSONArray("fieldList").add(JSONArray.of(sumKey, "合计"));
                declared.add(sumKey);
                for (Object rowObject : ds.getJSONArray("jsonData")) {
                    JSONObject row = (JSONObject) rowObject;
                    BigDecimal total = BigDecimal.ZERO;
                    for (String f : sum.getValue()) {
                        Object v = row.get(f);
                        if (v instanceof Number) total = total.add(new BigDecimal(v.toString()));
                    }
                    row.put(sumKey, total.signum() == 0 ? "" : total.stripTrailingZeros().toPlainString());
                }
            }
            for (Object rowObject : ds.getJSONArray("jsonData")) {
                JSONObject row = (JSONObject) rowObject;
                for (String name : declared) {
                    Object v = row.get(name);
                    if (v instanceof Number) {
                        BigDecimal d = new BigDecimal(v.toString()).stripTrailingZeros();
                        row.put(name, d.signum() == 0 ? "" : d.toPlainString());
                    }
                }
            }
        }
        return c;
    }

    /**
     * 解析列合计 =SUM(D4)：D 定位到明细行的列，取该列绑定的字段（可含多个引用，如小计列），
     * 在静态明细上求和；0 写空串，与逐格判零口径一致。列/行定位失败时返回空串，不猜测。
     */
    private String columnTotal(JSONObject config, String columnLetters, String rowNumber) {
        JSONObject rows = config.getJSONObject("customRows");
        String rowKey = Integer.toString(Integer.parseInt(rowNumber) - 1);
        JSONObject row = rows.getJSONObject(rowKey);
        JSONObject cells = row == null ? null : row.getJSONObject("cells");
        JSONObject cell = cells == null ? null : cells.getJSONObject(Integer.toString(columnIndex(columnLetters)));
        String text = cell == null ? null : cell.getString("text");
        if (text == null) {
            return "";
        }
        Set<String> refs = new LinkedHashSet<>();
        Matcher matcher = REF.matcher(text);
        while (matcher.find()) {
            refs.add(matcher.group(1) + "." + matcher.group(2));
        }
        if (refs.isEmpty()) {
            return "";
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Object item : config.getJSONArray("datasets")) {
            JSONObject ds = (JSONObject) item;
            String code = ds.getString("dbCode");
            List<String> fields = new ArrayList<>();
            for (String ref : refs) {
                if (ref.startsWith(code + ".")) {
                    fields.add(ref.substring(code.length() + 1));
                }
            }
            if (fields.isEmpty()) continue;
            for (Object rowObject : ds.getJSONArray("jsonData")) {
                JSONObject dataRow = (JSONObject) rowObject;
                for (String field : fields) {
                    Object value = dataRow.get(field);
                    if (value instanceof Number) {
                        total = total.add(new BigDecimal(value.toString()));
                    }
                }
            }
        }
        return total.signum() == 0 ? "" : total.stripTrailingZeros().toPlainString();
    }

    /** 表格软件列字母转 0 基下标（A→0，H→7，AA→26）。 */
    private static int columnIndex(String letters) {
        int index = 0;
        for (char ch : letters.toUpperCase(Locale.ROOT).toCharArray()) {
            index = index * 26 + (ch - 'A' + 1);
        }
        return index - 1;
    }

    private static String id() { return Long.toString(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE); }
    private static void require(boolean ok, String message) { if (!ok) throw new ServiceException(message); }
}

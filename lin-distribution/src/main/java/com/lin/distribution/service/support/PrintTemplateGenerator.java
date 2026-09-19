package com.lin.distribution.service.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lin.common.exception.ServiceException;
import com.lin.distribution.dto.PrintTemplateGenerateDTO;
import com.lin.distribution.service.DeliveryBatchService;
import com.lin.distribution.service.support.PrintDataContract.Form;
import com.lin.distribution.service.support.PrintDataContract.RowsShape;
import com.lin.distribution.vo.DeliveryMatrixVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 打印模板骨架生成器（P3 动态生成，PR-D4）
 *
 * <p>输入「形态 + 客户 + 日期」，输出 JimuReport 设计 JSON：</p>
 * <ul>
 *   <li><b>总单长表</b>：直接取通用骨架 {@code print-blueprint/matrix-long.json}
 *       （横向动态列，配送点增减模板零改动，客户无关）；</li>
 *   <li><b>点单平铺</b>：取通用骨架 {@code print-blueprint/flat-a4.json}；</li>
 *   <li><b>总单宽表</b>：按<b>当日实际列布局</b>动态拼装 c1..cN 槽位
 *       （列来源与打印取数同源：{@code DeliveryBatchService#selectMatrix}），保证「模板列=纸面列=数据列」。</li>
 * </ul>
 *
 * <p>与静态样板导入（P1 已退役）的本质区别：<b>不掺入任何静态数量/客户数据</b>，
 * 只生成版式骨架，数据由打印时经数据契约实时取。</p>
 *
 * @author dsh
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrintTemplateGenerator {

    /** 无客户/无布局时的宽表兜底槽位数（覆盖常见 7 点客户） */
    private static final int DEFAULT_SLOTS = 7;
    private static final String BLUEPRINT_DIR = "print-blueprint/";

    private final DeliveryBatchService deliveryBatchService;

    /**
     * 生成模板骨架
     *
     * @param req 生成请求
     * @return 生成结果（设计 JSON + 槽位/列元数据）
     */
    public GenerateResult generate(PrintTemplateGenerateDTO req) {
        if (req == null) {
            throw new ServiceException("生成请求不能为空");
        }
        Form form = PrintDataContract.formOf(req.getPrintForm());
        RowsShape shape = PrintDataContract.shapeOf(req.getRowsType());
        String paper = StringUtils.defaultIfBlank(req.getPaper(), "A4");
        String layout = StringUtils.defaultIfBlank(req.getLayout(),
                form == Form.MATRIX ? "landscape" : "portrait");

        if (form == Form.MATRIX && shape == RowsShape.WIDE) {
            List<ColumnMeta> columns = resolveColumns(req.getCustomerId(), req.getDeliveryDate());
            int slots = Math.max(columns.size(), 1);
            JSONObject sheet = buildWide(req.getTitle(), columns, slots, paper, layout);
            return new GenerateResult(form.name(), shape.name(), slots, columns, sheet.toJSONString());
        }

        JSONObject sheet = loadBlueprint(form == Form.FLAT ? "flat-a4" : "matrix-long");
        applyPaper(sheet, paper, layout);
        if (form == Form.FLAT && StringUtils.isNotBlank(req.getTitle())) {
            // 点单标题是静态文字，允许覆盖；总单标题用 ${hm.printTitle} 动态，不改
            putCellText(sheet, "0", "0", req.getTitle());
        }
        return new GenerateResult(form.name(), shape.name(), 0, List.of(), sheet.toJSONString());
    }

    /**
     * 解析宽表列（与打印取数同源）：按客户+日期取矩阵布局第 1 列块；
     * 无客户/取数失败时回退 DEFAULT_SLOTS 个占位列。
     */
    private List<ColumnMeta> resolveColumns(Long customerId, String deliveryDate) {
        if (customerId == null) {
            return placeholderColumns(DEFAULT_SLOTS);
        }
        String date = StringUtils.defaultIfBlank(deliveryDate, LocalDate.now().toString());
        try {
            DeliveryMatrixVO matrix = deliveryBatchService.selectMatrix(customerId, date);
            List<DeliveryMatrixVO.ColumnVO> block = matrix.getColumns().stream()
                    .filter(c -> c.getBlockNo() == null || c.getBlockNo() == 1)
                    .collect(Collectors.toList());
            if (block.isEmpty()) {
                return placeholderColumns(DEFAULT_SLOTS);
            }
            int slots = matrix.getColsPerPage() != null && matrix.getColsPerPage() > 0
                    ? matrix.getColsPerPage() : block.size();
            List<ColumnMeta> columns = new ArrayList<>();
            for (int i = 0; i < slots; i++) {
                DeliveryMatrixVO.ColumnVO col = i < block.size() ? block.get(i) : null;
                columns.add(new ColumnMeta(
                        col == null ? null : col.getDeptId(),
                        col == null ? "" : StringUtils.defaultString(col.getName()),
                        col == null ? null : col.getShiftCode()));
            }
            return columns;
        } catch (Exception e) {
            log.warn("[print-blueprint] 取矩阵列失败，回退占位槽位 customerId={} date={}: {}",
                    customerId, date, e.getMessage());
            return placeholderColumns(DEFAULT_SLOTS);
        }
    }

    private List<ColumnMeta> placeholderColumns(int slots) {
        List<ColumnMeta> columns = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            columns.add(new ColumnMeta(null, "", null));
        }
        return columns;
    }

    /**
     * 拼装宽表（槽位套打）设计：行=菜品，列=配送点固定槽位 c1..cN（列位绝对稳定）。
     * 槽位数必须与打印取数的 colsPerPage 一致，否则列位漂移（宽表方案唯一耦合点）。
     */
    private JSONObject buildWide(String title, List<ColumnMeta> columns, int slots,
                                 String paper, String layout) {
        int lastCol = slots + 4;          // 0..3 固定列 + slots + 小计
        JSONObject sheet = baseSheet(paper, layout);
        JSONObject rows = new JSONObject();

        // 行0：标题（跨全列）
        JSONObject r0 = new JSONObject();
        r0.put("cells", cells(Map.of("0", cell(title == null ? "${hm.printTitle}" : title, 1, merge(0, lastCol - 1)))));
        r0.put("height", 34);
        rows.put("0", r0);

        // 行1：日期 / 列块 / 合计
        JSONObject r1 = new JSONObject();
        r1.put("cells", cells(Map.of(
                "0", cell("配送日期：${hm.deliveryDate}", null, merge(0, 1)),
                "2", cell("列块：${hm.colBlockLabel}", null, null),
                "4", cell("合计：${hm.totalQuantity}（${hm.totalKinds} 项 / ${hm.pointCount} 点）",
                        null, merge(0, slots - 1)))));
        r1.put("height", 22);
        rows.put("1", r1);

        // 行2：表头（品名/规格/单位/备注 + c1Name..cNName + 小计）
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("0", cell("品名", 1, null));
        headers.put("1", cell("规格", 1, null));
        headers.put("2", cell("单位", 1, null));
        headers.put("3", cell("备注", 1, null));
        for (int i = 1; i <= slots; i++) {
            headers.put(String.valueOf(i + 3), cell("${hm.c" + i + "Name}", 1, null));
        }
        headers.put(String.valueOf(lastCol), cell("小计", 1, null));
        JSONObject r2 = new JSONObject();
        r2.put("cells", cells(headers));
        r2.put("height", 24);
        rows.put("2", r2);

        // 行3：数据（逐格绑定，槽位 c1..cN）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("0", cell("#{dm.productName}", null, null));
        data.put("1", cell("#{dm.productSpec}", null, null));
        data.put("2", cell("#{dm.productUnit}", null, null));
        data.put("3", cell("#{dm.remark}", null, null));
        for (int i = 1; i <= slots; i++) {
            data.put(String.valueOf(i + 3), cell("#{dm.c" + i + "}", null, null));
        }
        data.put(String.valueOf(lastCol), cell("#{dm.total}", null, null));
        JSONObject r3 = new JSONObject();
        r3.put("cells", cells(data));
        r3.put("height", 22);
        rows.put("3", r3);

        // 行4：合计
        JSONObject r4 = new JSONObject();
        r4.put("cells", cells(Map.of(
                "0", cell("合　计", null, merge(0, 2)),
                String.valueOf(lastCol), cell("${hm.totalQuantity}", 1, null))));
        r4.put("height", 24);
        rows.put("4", r4);

        rows.put("len", 100);
        sheet.put("rows", rows);
        sheet.put("cols", wideCols(slots));
        sheet.put("fixedPrintHeadRows", JSONArray.of(2));
        sheet.put("merges", new JSONArray());
        return sheet;
    }

    private JSONObject wideCols(int slots) {
        JSONObject cols = new JSONObject();
        cols.put("0", width(120));
        cols.put("1", width(60));
        cols.put("2", width(50));
        cols.put("3", width(70));
        for (int i = 1; i <= slots; i++) {
            cols.put(String.valueOf(i + 3), width(75));
        }
        cols.put(String.valueOf(slots + 4), width(70));
        cols.put("len", 100);
        return cols;
    }

    /** 通用骨架骨架字段（与积木设计器输出同构） */
    private JSONObject baseSheet(String paper, String layout) {
        JSONObject sheet = new JSONObject();
        sheet.put("loopBlockList", new JSONArray());
        JSONObject querySetting = new JSONObject();
        querySetting.put("izOpenQueryBar", false);
        querySetting.put("izDefaultQuery", true);
        sheet.put("querySetting", querySetting);
        sheet.put("printConfig", printConfig(paper, layout));
        JSONObject hidden = new JSONObject();
        hidden.put("rows", new JSONArray());
        hidden.put("cols", new JSONArray());
        sheet.put("hidden", hidden);
        JSONObject queryForm = new JSONObject();
        queryForm.put("useQueryForm", false);
        queryForm.put("dbKey", "");
        queryForm.put("idField", "");
        sheet.put("queryFormSetting", queryForm);
        sheet.put("dbexps", new JSONArray());
        sheet.put("dicts", new JSONArray());
        sheet.put("freeze", "A1");
        sheet.put("autofilter", new JSONObject());
        sheet.put("validations", new JSONArray());
        sheet.put("hiddenCells", new JSONArray());
        sheet.put("zonedEditionList", new JSONArray());
        sheet.put("pyGroupEngine", false);
        sheet.put("submitHandlers", new JSONArray());
        sheet.put("rpbar", new JSONObject(Map.of("show", true, "pageSize", "", "btnList", new JSONArray())));
        sheet.put("fixedPrintTailRows", new JSONArray());
        sheet.put("displayConfig", new JSONObject());
        sheet.put("background", false);
        sheet.put("name", "sheet1");
        sheet.put("styles", JSONArray.of(
                new JSONObject(Map.of("align", "center")),
                new JSONObject(Map.of("bgcolor", "#d9e1f2", "align", "center"))));
        sheet.put("isViewContentHorizontalCenter", false);
        return sheet;
    }

    private JSONObject printConfig(String paper, String layout) {
        int width = "A5".equalsIgnoreCase(paper) ? 148 : 210;
        int height = "A5".equalsIgnoreCase(paper) ? 210 : 297;
        JSONObject config = new JSONObject();
        config.put("paper", StringUtils.defaultIfBlank(paper, "A4"));
        config.put("width", width);
        config.put("height", height);
        config.put("definition", 1);
        config.put("isBackend", false);
        config.put("marginX", 8);
        config.put("marginY", 10);
        config.put("layout", StringUtils.defaultIfBlank(layout, "portrait"));
        return config;
    }

    private void applyPaper(JSONObject sheet, String paper, String layout) {
        sheet.put("printConfig", printConfig(paper, layout));
    }

    /** 覆盖某格文字（骨架微调用） */
    private void putCellText(JSONObject sheet, String rowKey, String colKey, String text) {
        JSONObject rows = sheet.getJSONObject("rows");
        if (rows == null) {
            return;
        }
        JSONObject row = rows.getJSONObject(rowKey);
        if (row == null) {
            return;
        }
        JSONObject cell = row.getJSONObject("cells").getJSONObject(colKey);
        if (cell != null) {
            cell.put("text", text);
        }
    }

    private JSONObject loadBlueprint(String name) {
        ClassPathResource resource = new ClassPathResource(BLUEPRINT_DIR + name + ".json");
        try (InputStream in = resource.getInputStream()) {
            String json = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            return JSONObject.parseObject(json);
        } catch (IOException e) {
            throw new ServiceException("打印骨架资源缺失：" + BLUEPRINT_DIR + name + ".json");
        }
    }

    // ---- 单元格构造小工具 ----

    private static JSONObject cell(String text, Integer style, JSONArray merge) {
        JSONObject cell = new JSONObject();
        cell.put("text", text);
        if (style != null) {
            cell.put("style", style);
        }
        if (merge != null) {
            cell.put("merge", merge);
        }
        return cell;
    }

    private static JSONObject cells(Map<String, Object> source) {
        JSONObject cells = new JSONObject();
        source.forEach(cells::put);
        return cells;
    }

    private static JSONArray merge(int rows, int cols) {
        return JSONArray.of(rows, cols);
    }

    private static JSONObject width(int px) {
        return new JSONObject(Map.of("width", px));
    }

    /** 列元数据（供前端展示与人工核对） */
    public record ColumnMeta(Long deptId, String name, String shiftCode) {
    }

    /** 生成结果 */
    public record GenerateResult(String printForm, String rowsType, int slots,
                                 List<ColumnMeta> columns, String designJson) {
    }
}

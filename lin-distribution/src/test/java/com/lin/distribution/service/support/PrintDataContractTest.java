package com.lin.distribution.service.support;

import com.lin.distribution.service.support.PrintDataContract.Form;
import com.lin.distribution.service.support.PrintDataContract.RowsShape;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 打印数据契约测试（PR-D3）：字段字典/数据集/参数的可执行口径。
 */
class PrintDataContractTest {

    @Test
    void 形态与行形态归一化_缺省值安全() {
        assertEquals(Form.MATRIX, PrintDataContract.formOf("MATRIX"));
        assertEquals(Form.MATRIX, PrintDataContract.formOf("matrix"));
        assertEquals(Form.FLAT, PrintDataContract.formOf("FLAT"));
        assertEquals(Form.FLAT, PrintDataContract.formOf(null));

        assertEquals(RowsShape.WIDE, PrintDataContract.shapeOf("WIDE"));
        assertEquals(RowsShape.LONG, PrintDataContract.shapeOf("long"));
        assertEquals(RowsShape.LONG, PrintDataContract.shapeOf(null), "缺省 LONG=通用推荐");
    }

    @Test
    void 点单数据集为hd与dd() {
        List<PrintDataContract.DataSet> sets = PrintDataContract.dataSets(Form.FLAT, RowsShape.LONG);
        assertEquals(List.of("hd", "dd"), sets.stream().map(PrintDataContract.DataSet::dbCode).toList());
        assertFalse(sets.get(0).list(), "hd 为单值表头");
        assertTrue(sets.get(1).list(), "dd 为列表明细");
        assertEquals("deliveryDataConvertAdapter", sets.get(0).apiConvert());
    }

    @Test
    void 总单长表数据集为hm_hc_dc_宽表为hm_dm() {
        assertEquals(List.of("hm", "hc", "dc"),
                PrintDataContract.dataSets(Form.MATRIX, RowsShape.LONG)
                        .stream().map(PrintDataContract.DataSet::dbCode).toList());
        assertEquals(List.of("hm", "dm"),
                PrintDataContract.dataSets(Form.MATRIX, RowsShape.WIDE)
                        .stream().map(PrintDataContract.DataSet::dbCode).toList());
    }

    @Test
    void 取数URL_长表带rowsType_long与空列策略_宽表不带() {
        String longUrl = PrintDataContract.apiQuery(Form.MATRIX, RowsShape.LONG);
        assertTrue(longUrl.contains("rowsType=long"));
        assertTrue(longUrl.contains("emptyCols=${emptyCols}"), "长表空列策略可经 URL 透传");
        assertTrue(longUrl.contains("${ticket}"), "票据必须透传");
        assertTrue(longUrl.contains("${customerId}") && longUrl.contains("${deliveryDate}"));

        String wideUrl = PrintDataContract.apiQuery(Form.MATRIX, RowsShape.WIDE);
        assertFalse(wideUrl.contains("rowsType="), "宽表走端点默认，不显式指定");
        assertFalse(wideUrl.contains("emptyCols="), "宽表槽位固定，不应用空列策略");

        String flatUrl = PrintDataContract.apiQuery(Form.FLAT, RowsShape.LONG);
        assertTrue(flatUrl.contains("${customerDeptId}"), "点单需配送点");
        assertTrue(flatUrl.contains("${deliveryDate}"));
    }

    @Test
    void 字段字典_每个数据集内编码唯一且覆盖真实输出字段() {
        // 编码唯一性按「数据集内」判定：head/columns/rows 分属不同数据集，可同名（如 seq）
        for (String dbCode : List.of("hd", "dd", "hm", "hc", "dc", "dm")) {
            List<PrintDataContract.Field> fields = PrintDataContract.fieldsOfDataSet(dbCode, RowsShape.LONG);
            assertFalse(fields.isEmpty(), dbCode + " 字段不应为空");
            Set<String> codes = new HashSet<>();
            for (PrintDataContract.Field field : fields) {
                assertTrue(codes.add(field.code()), dbCode + " 字段编码重复：" + field.code());
            }
        }
        // 合并视图（字段面板用）非空
        for (Form form : Form.values()) {
            for (RowsShape shape : RowsShape.values()) {
                assertFalse(PrintDataContract.fields(form, shape).isEmpty(), form + "/" + shape);
            }
        }
        // 字段名取自 PrintController 真实输出（不是设计文档里过时的 spec/unit）
        Set<String> flatRows = PrintDataContract.fieldsOfDataSet("dd", RowsShape.LONG)
                .stream().map(PrintDataContract.Field::code).collect(java.util.stream.Collectors.toSet());
        assertTrue(flatRows.containsAll(Set.of("seq", "productName", "productSpec", "productUnit",
                "num", "price", "amount", "acceptanceNum", "changeTag")));
        assertFalse(flatRows.contains("spec"), "旧文档字段名 spec 不得出现在契约中");
    }

    @Test
    void 参数_点单含配送点_总单含列块与空列策略() {
        Set<String> flat = PrintDataContract.params(Form.FLAT)
                .stream().map(PrintDataContract.Param::code).collect(java.util.stream.Collectors.toSet());
        assertTrue(flat.containsAll(Set.of("deliveryOrderId", "customerId", "customerDeptId",
                "deliveryDate", "ticket")));

        Set<String> matrix = PrintDataContract.params(Form.MATRIX)
                .stream().map(PrintDataContract.Param::code).collect(java.util.stream.Collectors.toSet());
        assertTrue(matrix.containsAll(Set.of("deliveryOrderId", "customerId", "deliveryDate",
                "colBlock", "emptyCols", "ticket")));
    }
}

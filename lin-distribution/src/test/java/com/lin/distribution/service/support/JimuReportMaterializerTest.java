package com.lin.distribution.service.support;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.service.support.PrintDataContract.Form;
import com.lin.distribution.service.support.PrintDataContract.RowsShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报表物化器测试（PR-D3）：契约驱动接线对齐 + 回执钩子补齐/升级。
 */
@ExtendWith(MockitoExtension.class)
class JimuReportMaterializerTest {

    private static final String REPORT = "RPT1";

    @Mock
    private JdbcTemplate jdbc;

    private JimuReportMaterializer materializer;

    @BeforeEach
    void setUp() {
        materializer = new JimuReportMaterializer(jdbc);
    }

    private void stubDesign(String design) {
        when(jdbc.queryForList(eq("select json_str from jimu_report where id = ?"), eq(String.class), eq(REPORT)))
                .thenReturn(design == null ? List.of() : List.of(design));
    }

    private void stubCodes(String... codes) {
        when(jdbc.queryForList(eq("select db_code from jimu_report_db where jimu_report_id = ?"),
                eq(String.class), eq(REPORT))).thenReturn(List.of(codes));
    }

    private void stubDataSets(Map<String, Object>... rows) {
        when(jdbc.queryForList(eq("select id, db_code from jimu_report_db where jimu_report_id = ?"),
                eq(REPORT))).thenReturn(List.of(rows));
    }

    private void stubJs(String js) {
        when(jdbc.queryForList(eq("select js_str from jimu_report where id = ?"), eq(String.class), eq(REPORT)))
                .thenReturn(js == null ? java.util.Collections.singletonList(null) : List.of(js));
    }

    @Test
    void 报表不存在时拒绝物化() {
        stubDesign(null);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> materializer.ensureMaterialized(REPORT, Form.MATRIX));
        assertTrue(ex.getMessage().contains("报表不存在"));
        assertThrows(ServiceException.class, () -> materializer.ensureMaterialized(" ", Form.MATRIX));
    }

    @Test
    void 探测行形态_dc为长表_dm为宽表_空报表默认长表() {
        stubCodes("hm", "hc", "dc");
        assertEquals(RowsShape.LONG, materializer.detectShape(REPORT, Form.MATRIX));

        stubCodes("hm", "dm");
        assertEquals(RowsShape.WIDE, materializer.detectShape(REPORT, Form.MATRIX));

        stubCodes();
        assertEquals(RowsShape.LONG, materializer.detectShape(REPORT, Form.MATRIX));
    }

    @Test
    void 对齐已有长表数据集_更新URL为配置基址并补齐参数与钩子() {
        stubDesign("{\"name\":\"sheet1\"}");
        stubCodes("hm", "hc", "dc");
        stubDataSets(Map.of("id", "D1", "db_code", "hm"),
                Map.of("id", "D2", "db_code", "hc"),
                Map.of("id", "D3", "db_code", "dc"));
        stubJs("(function(){var s=document.createElement(\"script\");s.src=\"/jmreport/desreport_/ext/print-annotation.js?v=10\";document.head.appendChild(s);})()");
        lenient().when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        JimuReportMaterializer.MaterializeResult result = materializer.ensureMaterialized(REPORT, Form.MATRIX);

        assertEquals(0, result.dataSetInserted(), "已有数据集不重复插入");
        assertEquals(3, result.dataSetUpdated(), "hm/hc/dc 三套对齐");
        assertTrue(result.paramInserted() > 0, "参数按契约补齐");
        assertTrue(result.annotationHookUpdated(), "v=10 应升级到 v=11");
        assertEquals("LONG", result.rowsType());

        // 数据集 URL 必须来自配置基址 + 契约端点 + rowsType=long（修 localhost 硬编码）
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, atLeastOnce()).update(startsWith("update jimu_report_db set api_url"), args.capture());
        String apiUrl = String.valueOf(args.getValue()[0]);
        assertTrue(apiUrl.startsWith(JimuReportMaterializer.DEFAULT_API_BASE_URL), apiUrl);
        assertTrue(apiUrl.contains("/print/deliveryMatrixData?"), apiUrl);
        assertTrue(apiUrl.contains("rowsType=long"), apiUrl);
        assertTrue(apiUrl.contains("${ticket}"), apiUrl);
    }

    @Test
    void 空报表补齐点单数据集与钩子() {
        stubDesign("{\"name\":\"sheet1\"}");
        stubDataSets(); // 空报表（FLAT 不探测行形态）
        stubJs(null);
        lenient().when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        JimuReportMaterializer.MaterializeResult result = materializer.ensureMaterialized(REPORT, Form.FLAT);

        assertEquals(2, result.dataSetInserted(), "hd/dd 两套补齐");
        assertEquals(0, result.dataSetUpdated());
        assertTrue(result.annotationHookUpdated(), "空 js_str 应写入钩子");
    }

    @Test
    void 钩子已是当前版本时不重复写() {
        stubDesign("{\"name\":\"sheet1\"}");
        stubCodes("hm", "hc", "dc");
        stubDataSets(Map.of("id", "D1", "db_code", "hm"),
                Map.of("id", "D2", "db_code", "hc"),
                Map.of("id", "D3", "db_code", "dc"));
        stubJs("x.print-annotation.js?v=" + JimuReportMaterializer.DEFAULT_ANNOTATION_VERSION);
        lenient().when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        JimuReportMaterializer.MaterializeResult result = materializer.ensureMaterialized(REPORT, Form.MATRIX);

        assertFalse(result.annotationHookUpdated(), "版本一致不重复写");
        verify(jdbc, never()).update(startsWith("update jimu_report set js_str"), any(Object[].class));
    }
}

package com.lin.distribution.service.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lin.common.exception.ServiceException;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.mapper.PrintTemplateMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JimuSampleImporterTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PrintTemplateMapper mapper = mock(PrintTemplateMapper.class);
    private final JimuSampleImporter importer = new JimuSampleImporter(jdbc, mapper, new TemplateContentGovernor());

    private String source() throws Exception {
        try (var stream = getClass().getResourceAsStream("/print/jinman-source.json")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 大长江源样张：目的地×班次四列 + 备注列，含 IF 判零包裹 color 与 =SUM(D4) 列合计行。 */
    private String dachangjiangSource() throws Exception {
        try (var stream = getClass().getResourceAsStream("/print/dachangjiang-source.json")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void preservesAll44RowsAndOriginalLayout() throws Exception {
        JSONObject config = importer.validate(source());
        assertEquals(44, config.getJSONArray("datasets").getJSONObject(0).getJSONArray("jsonData").size());
        JSONObject sheet = importer.sheet(config, "123");
        assertEquals(config.getJSONObject("customRows"), sheet.getJSONObject("rows"));
        assertEquals(config.getJSONArray("customMerges"), sheet.getJSONArray("merges"));
        assertEquals(6, sheet.getJSONArray("styles").size());
        assertTrue(sheet.getJSONObject("printConfig").getBooleanValue("watermarkShow"));
        new TemplateContentGovernor().validateImport(sheet);
        JSONObject normalized = importer.normalize(config);
        JSONObject dataset = normalized.getJSONArray("datasets").getJSONObject(0);
        assertEquals(12, dataset.getJSONArray("fieldList").size());
        assertEquals("40", dataset.getJSONArray("jsonData").getJSONObject(0).getString("sum1"));
        assertEquals("", dataset.getJSONArray("jsonData").getJSONObject(0).getString("weibu"));
        assertEquals("=color('#{jinmanDs.name}','#{jinmanDs.textColor}','#{jinmanDs.bgColor}')",
                normalized.getJSONObject("customRows").getJSONObject("3").getJSONObject("cells")
                        .getJSONObject("1").getString("text"));
        importer.validate(normalized.toJSONString());
    }

    @Test
    void normalizesConditionalColorAndColumnSumFooter() throws Exception {
        JSONObject config = importer.validate(dachangjiangSource());
        JSONObject normalized = importer.normalize(config);
        JSONObject cells = normalized.getJSONObject("customRows");
        // 数量列：判零外壳与取色都去掉，降为纯绑定（引擎 color() 对空值输出 null，无法兼顾留白）
        assertEquals("#{dachangjiangDs.hlb}",
                cells.getJSONObject("3").getJSONObject("cells").getJSONObject("3").getString("text"));
        // 列合计按该列绑定字段在静态明细上预计算（D=华铃白班 816.4，H=小计四列合计 1270.8）
        assertEquals("816.4", cells.getJSONObject("4").getJSONObject("cells").getJSONObject("3").getString("text"));
        assertEquals("289.4", cells.getJSONObject("4").getJSONObject("cells").getJSONObject("4").getString("text"));
        assertEquals("1270.8", cells.getJSONObject("4").getJSONObject("cells").getJSONObject("7").getString("text"));
        // 小计列同样舍弃取色，合计预计算为字段；sum1 为逐行小计
        assertEquals("#{dachangjiangDs.sum1}",
                cells.getJSONObject("3").getJSONObject("cells").getJSONObject("7").getString("text"));
        // 品名/单位不会为空，保留逐行取色（红字品项标记）
        assertEquals("=color('#{dachangjiangDs.name}','#{dachangjiangDs.nameColor}','#{dachangjiangDs.bgColor}')",
                cells.getJSONObject("3").getJSONObject("cells").getJSONObject("1").getString("text"));
        assertEquals("30", normalized.getJSONArray("datasets").getJSONObject(0)
                .getJSONArray("jsonData").getJSONObject(0).getString("sum1"));
        assertEquals("225", normalized.getJSONArray("datasets").getJSONObject(0)
                .getJSONArray("jsonData").getJSONObject(33).getString("sum1"));
        assertEquals("#{dachangjiangDs.remark}", cells.getJSONObject("3").getJSONObject("cells").getJSONObject("8").getString("text"));
        // 零值仍按空串归一化（判零改由数据承担），且归一化结果可再次通过校验
        JSONObject dataset = normalized.getJSONArray("datasets").getJSONObject(0);
        assertEquals("", dataset.getJSONArray("jsonData").getJSONObject(0).getString("hln"));
        importer.validate(normalized.toJSONString());
    }

    @Test
    void leavesUnknownColumnSumReferenceBlankInsteadOfGuessing() throws Exception {
        JSONObject c = JSON.parseObject(dachangjiangSource());
        c.getJSONObject("customRows").getJSONObject("4").getJSONObject("cells").getJSONObject("3").put("text", "=SUM(Z9)");
        assertEquals("", importer.normalize(c).getJSONObject("customRows").getJSONObject("4")
                .getJSONObject("cells").getJSONObject("3").getString("text"));
    }

    @Test
    void createsReportDatasetFieldsAndUnboundDraftReferencingReportId() throws Exception {
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("test");
            when(mapper.insertPrintTemplate(any())).thenReturn(1);
            assertEquals(1, importer.importSample(source()));
            ArgumentCaptor<PrintTemplate> template = ArgumentCaptor.forClass(PrintTemplate.class);
            verify(mapper).insertPrintTemplate(template.capture());
            PrintTemplate t = template.getValue();
            assertTrue(t.getContent().matches("[0-9]+"));
            assertEquals("MATRIX", t.getPrintForm());
            assertEquals(0, t.getStatus());
            assertEquals("0", t.getIsDefault());
            assertEquals(3, t.getBindType());
            assertTrue(t.getTestWatermark());
            // 1 report + 1 dataset + 11 source fields + 1 computed total.
            assertEquals(14, mockingDetails(jdbc).getInvocations().size());
            assertEquals(t.getContent(), mockingDetails(jdbc).getInvocations().iterator().next().getArgument(1));
        }
    }

    @Test
    void rejectsInvalidStyleUnknownFieldAndNonStaticDatasetBeforeWrites() throws Exception {
        JSONObject c = JSON.parseObject(source());
        c.getJSONObject("customRows").getJSONObject("3").getJSONObject("cells").getJSONObject("1").put("style", 999);
        assertThrows(ServiceException.class, () -> importer.importSample(c.toJSONString()));
        c.getJSONObject("customRows").getJSONObject("3").getJSONObject("cells").getJSONObject("1").put("style", 0);
        c.getJSONObject("customRows").getJSONObject("3").getJSONObject("cells").getJSONObject("1").put("text", "#{jinmanDs.missing}");
        assertThrows(ServiceException.class, () -> importer.importSample(c.toJSONString()));
        JSONObject api = JSON.parseObject(source());
        api.getJSONArray("datasets").getJSONObject(0).put("dbType", "1");
        assertThrows(ServiceException.class, () -> importer.importSample(api.toJSONString()));
        verifyNoInteractions(jdbc, mapper);
    }
}

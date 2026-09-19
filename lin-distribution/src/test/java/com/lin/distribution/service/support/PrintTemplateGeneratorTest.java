package com.lin.distribution.service.support;

import com.alibaba.fastjson2.JSONObject;
import com.lin.distribution.dto.PrintTemplateGenerateDTO;
import com.lin.distribution.service.DeliveryBatchService;
import com.lin.distribution.vo.DeliveryMatrixVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 模板骨架生成器测试（P3 动态生成）：长表/点单取通用骨架，宽表按真实列布局拼槽位。
 */
@ExtendWith(MockitoExtension.class)
class PrintTemplateGeneratorTest {

    @Mock
    private DeliveryBatchService deliveryBatchService;

    private PrintTemplateGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new PrintTemplateGenerator(deliveryBatchService);
    }

    private PrintTemplateGenerateDTO req(String form, String rowsType, Long customerId, String date) {
        PrintTemplateGenerateDTO dto = new PrintTemplateGenerateDTO();
        dto.setPrintForm(form);
        dto.setRowsType(rowsType);
        dto.setCustomerId(customerId);
        dto.setDeliveryDate(date);
        return dto;
    }

    private DeliveryMatrixVO matrix(int colsPerPage, String... names) {
        DeliveryMatrixVO vo = new DeliveryMatrixVO();
        vo.setColsPerPage(colsPerPage);
        List<DeliveryMatrixVO.ColumnVO> columns = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            columns.add(DeliveryMatrixVO.ColumnVO.builder()
                    .deptId((long) (i + 1))
                    .name(names[i])
                    .blockNo(1)
                    .build());
        }
        vo.setColumns(columns);
        return vo;
    }

    @Test
    void 点单生成_a4骨架_含hd与dd绑定_槽位为0() {
        PrintTemplateGenerator.GenerateResult result = generator.generate(req("FLAT", "LONG", null, null));

        assertEquals("FLAT", result.printForm());
        assertEquals(0, result.slots());
        JSONObject sheet = JSONObject.parseObject(result.designJson());
        assertEquals("portrait", sheet.getJSONObject("printConfig").getString("layout"));
        String json = result.designJson();
        assertTrue(json.contains("${hd.deliveryPointName}"), "点单表头绑定");
        assertTrue(json.contains("#{dd.productName}"), "点单明细绑定");
    }

    @Test
    void 点单可覆盖标题() {
        PrintTemplateGenerateDTO dto = req("FLAT", "LONG", null, null);
        dto.setTitle("丽宫送货单");
        PrintTemplateGenerator.GenerateResult result = generator.generate(dto);

        assertTrue(result.designJson().contains("丽宫送货单"));
    }

    @Test
    void 总单长表生成_通用骨架_横向动态列_客户无关() {
        PrintTemplateGenerator.GenerateResult result = generator.generate(req("MATRIX", "LONG", 10L, "2026-09-01"));

        assertEquals("MATRIX", result.printForm());
        assertEquals("LONG", result.rowsType());
        JSONObject sheet = JSONObject.parseObject(result.designJson());
        assertEquals("landscape", sheet.getJSONObject("printConfig").getString("layout"));
        String json = result.designJson();
        assertTrue(json.contains("#{dc.groupRight(deptLabel)}"), "横向动态列分组");
        assertTrue(json.contains("#{dc.dynamic(num)}"), "动态数据格");
        assertFalse(json.contains("#{dm.c1}"), "长表不应出现固定槽位");
    }

    @Test
    void 总单宽表生成_按真实列布局拼槽位_模板列等于纸面列() {
        when(deliveryBatchService.selectMatrix(eq(10L), anyString()))
                .thenReturn(matrix(3, "华铃白班", "华铃夜班", "棠下白班"));

        PrintTemplateGenerator.GenerateResult result = generator.generate(req("MATRIX", "WIDE", 10L, "2026-09-01"));

        assertEquals("WIDE", result.rowsType());
        assertEquals(3, result.slots(), "槽位数=colsPerPage，与打印取数一致");
        assertEquals(3, result.columns().size());
        String json = result.designJson();
        assertTrue(json.contains("#{dm.c1}") && json.contains("#{dm.c3}"));
        assertFalse(json.contains("#{dm.c4}"), "超出槽位不得出现");
        assertTrue(json.contains("${hm.c1Name}") && json.contains("${hm.c3Name}"));
        assertTrue(json.contains("#{dm.productName}") && json.contains("${hm.totalQuantity}"));
    }

    @Test
    void 宽表无客户时回退占位槽位() {
        PrintTemplateGenerator.GenerateResult result = generator.generate(req("MATRIX", "WIDE", null, null));

        assertEquals(7, result.slots(), "兜底 7 槽覆盖常见七点客户");
        assertTrue(result.designJson().contains("#{dm.c7}"));
        assertFalse(result.designJson().contains("#{dm.c8}"));
    }

    @Test
    void 宽表取数失败时回退占位槽位不抛异常() {
        lenient().when(deliveryBatchService.selectMatrix(eq(99L), anyString()))
                .thenThrow(new RuntimeException("boom"));

        PrintTemplateGenerator.GenerateResult result = generator.generate(req("MATRIX", "WIDE", 99L, "2026-09-01"));

        assertEquals(7, result.slots());
        assertNotNull(result.designJson());
    }

    @Test
    void 纸张与方向可配置() {
        PrintTemplateGenerateDTO dto = req("MATRIX", "LONG", null, null);
        dto.setPaper("A5");
        dto.setLayout("portrait");
        PrintTemplateGenerator.GenerateResult result = generator.generate(dto);

        JSONObject config = JSONObject.parseObject(result.designJson()).getJSONObject("printConfig");
        assertEquals("A5", config.getString("paper"));
        assertEquals("portrait", config.getString("layout"));
        assertEquals(148, config.getIntValue("width"));
        assertEquals(210, config.getIntValue("height"));
    }
}

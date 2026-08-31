package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.PrintTemplateStatus;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.mapper.PrintPreviewLogMapper;
import com.lin.distribution.mapper.PrintTemplateMapper;
import com.lin.distribution.mapper.PrintTemplateVersionMapper;
import com.lin.distribution.service.support.TemplateContentGovernor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打印模板导入导出测试（W0-6：全有或全无安全校验、脱敏、未绑定草稿、网络资源拒绝）
 */
@ExtendWith(MockitoExtension.class)
class PrintTemplateIoServiceImplTest {

    @Mock
    private PrintTemplateMapper printTemplateMapper;
    @Mock
    private PrintTemplateVersionMapper printTemplateVersionMapper;
    @Mock
    private PrintPreviewLogMapper printPreviewLogMapper;

    /** 直接构造：注入真实治理器（不依赖 Spring 容器） */
    private PrintTemplateServiceImpl service() {
        return new PrintTemplateServiceImpl(
                printTemplateMapper, printTemplateVersionMapper, printPreviewLogMapper,
                new TemplateContentGovernor());
    }

    private PrintTemplate publishedTemplate() {
        PrintTemplate t = new PrintTemplate();
        t.setId(1L);
        t.setName("送货单模板A");
        t.setType(0);
        t.setRenderEngine("jimureport");
        t.setCopies(1);
        // content 含 schemaVersion + 本地资源引用（无网络资源）
        t.setContent("{\"schemaVersion\":1,\"paper\":\"A4\",\"logo\":{\"src\":\"/profile/print/assets/a.png\"}}");
        t.setStatus(PrintTemplateStatus.PUBLISHED.getCode());
        return t;
    }

    @Test
    void 导出应脱敏并携带格式与schemaVersion() {
        when(printTemplateMapper.selectPrintTemplateById(1L)).thenReturn(publishedTemplate());
        Map<String, Object> pkg = service().exportTemplate(1L, false);
        assertEquals(TemplateContentGovernor.PACKAGE_FORMAT, pkg.get("format"));
        assertEquals(TemplateContentGovernor.SCHEMA_VERSION, pkg.get("schemaVersion"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> templates = (List<Map<String, Object>>) pkg.get("templates");
        assertEquals(1, templates.size());
        Map<String, Object> tpl = templates.get(0);
        assertEquals("送货单模板A", tpl.get("name"));
        // content 为对象且含 schemaVersion
        Object content = tpl.get("content");
        assertTrue(content instanceof Map);
        assertTrue(((Map<?, ?>) content).containsKey("schemaVersion"));
    }

    @Test
    void 导入合法包应落库为未绑定草稿并重命名() {
        String pkg = "{\"format\":\"" + TemplateContentGovernor.PACKAGE_FORMAT + "\","
                + "\"schemaVersion\":1,\"exportedAt\":\"x\",\"exportedBy\":\"admin\","
                + "\"templates\":[{\"name\":\"外部模板\",\"type\":0,\"renderEngine\":\"jimureport\",\"copies\":2,"
                + "\"content\":{\"schemaVersion\":1,\"paper\":\"A4\",\"logo\":{\"src\":\"/profile/print/assets/b.png\"}}}]"
                + "}";
        when(printTemplateMapper.insertPrintTemplate(any(PrintTemplate.class))).thenAnswer(inv -> {
            ((PrintTemplate) inv.getArgument(0)).setId(99L);
            return 1;
        });

        int count = service().importTemplates(pkg);

        assertEquals(1, count);
        ArgumentCaptor<PrintTemplate> captor = ArgumentCaptor.forClass(PrintTemplate.class);
        verify(printTemplateMapper).insertPrintTemplate(captor.capture());
        PrintTemplate inserted = captor.getValue();
        // 重命名：导入_原名_时间戳
        assertTrue(inserted.getName().startsWith("导入_外部模板_"), "名称前缀应为「导入_外部模板_」: " + inserted.getName());
        // 未绑定草稿
        assertEquals(3, inserted.getBindType());
        assertEquals(0L, inserted.getCustomerId());
        assertEquals("0", inserted.getIsDefault());
        assertEquals(PrintTemplateStatus.DRAFT.getCode(), inserted.getStatus());
        assertEquals(2, inserted.getCopies());
        // content 含 schemaVersion 且保留了结构
        assertTrue(inserted.getContent().contains("\"schemaVersion\":1"));
    }

    @Test
    void 导入包格式不兼容应拒绝() {
        String pkg = "{\"format\":\"unknown\",\"schemaVersion\":1,\"templates\":[]}";
        assertThrows(ServiceException.class, () -> service().importTemplates(pkg));
    }

    @Test
    void 导入schemaVersion不兼容应拒绝() {
        String pkg = "{\"format\":\"" + TemplateContentGovernor.PACKAGE_FORMAT + "\",\"schemaVersion\":99,\"templates\":[]}";
        assertThrows(ServiceException.class, () -> service().importTemplates(pkg));
    }

    @Test
    void 导入含网络资源应整体拒绝且不落库() {
        String pkg = "{\"format\":\"" + TemplateContentGovernor.PACKAGE_FORMAT + "\","
                + "\"schemaVersion\":1,\"templates\":[{\"name\":\"恶意模板\",\"type\":0,\"copies\":1,"
                + "\"content\":{\"schemaVersion\":1,\"logo\":{\"src\":\"https://evil.example.com/x.png\"}}}"
                + "]}";
        // 全有或全无：抛异常，不调用 insertPrintTemplate
        assertThrows(ServiceException.class, () -> service().importTemplates(pkg));
        verify(printTemplateMapper, org.mockito.Mockito.never())
                .insertPrintTemplate(any(PrintTemplate.class));
    }

    @Test
    void 导入超出20MB上限应拒绝() {
        // 构造一个超过 20MB 字符数的字符串（用空格填充）
        StringBuilder sb = new StringBuilder("{\"format\":\"" + TemplateContentGovernor.PACKAGE_FORMAT
                + "\",\"schemaVersion\":1,\"templates\":[],\"pad\":\"");
        long target = TemplateContentGovernor.IMPORT_MAX_BYTES + 100;
        while (sb.length() < target) {
            sb.append("                                                                ");
        }
        sb.append("\"}");
        assertThrows(ServiceException.class, () -> service().importTemplates(sb.toString()));
    }

    @Test
    void 导入模板类型非法应拒绝() {
        String pkg = "{\"format\":\"" + TemplateContentGovernor.PACKAGE_FORMAT + "\","
                + "\"schemaVersion\":1,\"templates\":[{\"name\":\"x\",\"type\":9,\"copies\":1,"
                + "\"content\":{\"schemaVersion\":1}}]}";
        assertThrows(ServiceException.class, () -> service().importTemplates(pkg));
    }

    @Test
    void 导入空包应拒绝() {
        assertThrows(ServiceException.class, () -> service().importTemplates(""));
        assertThrows(ServiceException.class, () -> service().importTemplates(null));
    }
}

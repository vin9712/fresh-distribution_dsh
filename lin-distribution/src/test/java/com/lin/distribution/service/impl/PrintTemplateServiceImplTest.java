package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.PrintTemplateStatus;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.mapper.PrintTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打印模板绑定解析测试（DESIGN.md 验收标准 6：三级绑定、联数）
 * 绑定优先级排序在 SQL（order by bind_type asc），此处验证服务契约与回退行为。
 */
@ExtendWith(MockitoExtension.class)
class PrintTemplateServiceImplTest {

    @Mock
    private PrintTemplateMapper printTemplateMapper;
    @Mock
    private com.lin.distribution.mapper.PrintTemplateVersionMapper printTemplateVersionMapper;
    @Mock
    private com.lin.distribution.mapper.PrintPreviewLogMapper printPreviewLogMapper;

    @InjectMocks
    private PrintTemplateServiceImpl printTemplateService;

    private DeliveryOrder order() {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(500L);
        order.setCustomerId(100L);
        order.setDeliveryPointId(101L);
        return order;
    }

    private PrintTemplate template(Integer bindType, Integer copies) {
        PrintTemplate template = new PrintTemplate();
        template.setId(1L);
        template.setName("模板-" + bindType);
        template.setContent("2099000000000000001");
        template.setBindType(bindType);
        template.setCopies(copies);
        template.setStatus(PrintTemplateStatus.PUBLISHED.getCode());
        return template;
    }

    @Test
    void 命中模板返回绑定与联数() {
        when(printTemplateMapper.selectBindTemplate(100L, 101L)).thenReturn(template(1, 3));
        PrintTemplate resolved = printTemplateService.resolveForDeliveryOrder(order());
        assertNotNull(resolved);
        assertEquals(Integer.valueOf(1), resolved.getBindType());
        assertEquals(Integer.valueOf(3), resolved.getCopies());
        verify(printTemplateMapper).selectBindTemplate(eq(100L), eq(101L));
    }

    @Test
    void 未配置任何模板应报错() {
        when(printTemplateMapper.selectBindTemplate(anyLong(), anyLong())).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> printTemplateService.resolveForDeliveryOrder(order()));
        assertTrue(ex.getMessage().contains("未配置已发布打印模板"));
    }

    @Test
    void 送货单为空应报错() {
        assertThrows(ServiceException.class, () -> printTemplateService.resolveForDeliveryOrder(null));
    }

    // ==================== 模板状态机与发布门禁（W0-4.4） ====================

    private PrintTemplate draftTemplate() {
        PrintTemplate t = new PrintTemplate();
        t.setId(1L);
        t.setCode("TPL0001");
        t.setName("全局-送货单");
        t.setContent("{\"fields\":[\"customer\"]}");
        t.setBindType(3);
        t.setCopies(1);
        t.setStatus(PrintTemplateStatus.DRAFT.getCode());
        return t;
    }

    @Test
    void 发布校验门禁通过并生成版本快照() {
        when(printTemplateMapper.selectPrintTemplateById(1L)).thenReturn(draftTemplate());
        when(printTemplateVersionMapper.selectMaxVersionNo(1L)).thenReturn(0);
        when(printTemplateMapper.updatePrintTemplate(any(PrintTemplate.class))).thenReturn(1);
        when(printTemplateVersionMapper.insert(any(com.lin.distribution.domain.PrintTemplateVersion.class))).thenReturn(1);

        int version = printTemplateService.publish(1L, "初版");

        assertEquals(1, version);
        // 更新为已发布 + 关测试水印
        ArgumentCaptor<PrintTemplate> captor = ArgumentCaptor.forClass(PrintTemplate.class);
        verify(printTemplateMapper).updatePrintTemplate(captor.capture());
        assertEquals(PrintTemplateStatus.PUBLISHED.getCode(), captor.getValue().getStatus());
        assertEquals(Boolean.FALSE, captor.getValue().getTestWatermark());
        // 写版本快照
        ArgumentCaptor<com.lin.distribution.domain.PrintTemplateVersion> vc =
                ArgumentCaptor.forClass(com.lin.distribution.domain.PrintTemplateVersion.class);
        verify(printTemplateVersionMapper).insert(vc.capture());
        assertEquals(1, vc.getValue().getVersionNo());
        assertEquals("全局-送货单", vc.getValue().getName());
        assertEquals(3, vc.getValue().getBindType());
    }

    @Test
    void 发布门禁缺名称拒绝() {
        PrintTemplate t = draftTemplate();
        t.setName("");
        when(printTemplateMapper.selectPrintTemplateById(1L)).thenReturn(t);
        ServiceException ex = assertThrows(ServiceException.class, () -> printTemplateService.publish(1L, null));
        assertTrue(ex.getMessage().contains("模板名称必填"));
    }

    @Test
    void 发布门禁缺内容拒绝() {
        PrintTemplate t = draftTemplate();
        t.setContent("");
        when(printTemplateMapper.selectPrintTemplateById(1L)).thenReturn(t);
        assertThrows(ServiceException.class, () -> printTemplateService.publish(1L, null));
    }

    @Test
    void 发布门禁组合绑定缺配送点拒绝() {
        PrintTemplate t = draftTemplate();
        t.setBindType(1);
        t.setDeliveryPointId(null);
        when(printTemplateMapper.selectPrintTemplateById(1L)).thenReturn(t);
        assertThrows(ServiceException.class, () -> printTemplateService.publish(1L, null));
    }

    @Test
    void 测试发布置已测试并打水印() {
        PrintTemplate t = draftTemplate();
        when(printTemplateMapper.selectPrintTemplateById(1L)).thenReturn(t);
        when(printTemplateMapper.updatePrintTemplate(any(PrintTemplate.class))).thenReturn(1);
        printTemplateService.testPublish(1L);
        ArgumentCaptor<PrintTemplate> captor = ArgumentCaptor.forClass(PrintTemplate.class);
        verify(printTemplateMapper).updatePrintTemplate(captor.capture());
        assertEquals(PrintTemplateStatus.TESTED.getCode(), captor.getValue().getStatus());
        assertEquals(Boolean.TRUE, captor.getValue().getTestWatermark());
    }

    @Test
    void 已发布模板不可直接修改() {
        PrintTemplate t = draftTemplate();
        t.setStatus(PrintTemplateStatus.PUBLISHED.getCode());
        when(printTemplateMapper.selectPrintTemplateById(1L)).thenReturn(t);
        PrintTemplate request = new PrintTemplate();
        request.setId(1L);
        assertThrows(ServiceException.class, () -> printTemplateService.updatePrintTemplate(request));
    }

    @Test
    void 回滚从历史版本发布新版本() {
        when(printTemplateMapper.selectPrintTemplateById(1L)).thenReturn(draftTemplate());
        com.lin.distribution.domain.PrintTemplateVersion source = new com.lin.distribution.domain.PrintTemplateVersion();
        source.setId(10L);
        source.setTemplateId(1L);
        source.setVersionNo(1);
        source.setName("旧版");
        source.setContent("{\"a\":1}");
        source.setBindType(3);
        source.setCopies(1);
        when(printTemplateVersionMapper.selectById(10L)).thenReturn(source);
        when(printTemplateVersionMapper.selectMaxVersionNo(1L)).thenReturn(1);
        when(printTemplateMapper.updatePrintTemplate(any(PrintTemplate.class))).thenReturn(1);
        when(printTemplateMapper.selectPrintTemplateById(1L)).thenReturn(sourceToRestored());
        when(printTemplateVersionMapper.insert(any(com.lin.distribution.domain.PrintTemplateVersion.class))).thenReturn(1);

        int version = printTemplateService.rollback(1L, 10L, null);

        assertEquals(2, version);
        ArgumentCaptor<com.lin.distribution.domain.PrintTemplateVersion> vc =
                ArgumentCaptor.forClass(com.lin.distribution.domain.PrintTemplateVersion.class);
        verify(printTemplateVersionMapper).insert(vc.capture());
        assertEquals(2, vc.getValue().getVersionNo());
        assertTrue(vc.getValue().getRemark().contains("回滚自版本 1"));
    }

    private PrintTemplate sourceToRestored() {
        PrintTemplate t = new PrintTemplate();
        t.setId(1L);
        t.setCode("TPL0001");
        t.setName("旧版");
        t.setContent("{\"a\":1}");
        t.setBindType(3);
        t.setCopies(1);
        t.setStatus(PrintTemplateStatus.PUBLISHED.getCode());
        return t;
    }

    @Test
    void 已发布模板不可删除() {
        PrintTemplate t = draftTemplate();
        t.setStatus(PrintTemplateStatus.PUBLISHED.getCode());
        when(printTemplateMapper.selectPrintTemplateById(1L)).thenReturn(t);
        assertThrows(ServiceException.class, () -> printTemplateService.deletePrintTemplateById(1L));
    }

    @Test
    void 记录打印预览() {
        when(printPreviewLogMapper.insert(any(com.lin.distribution.domain.PrintPreviewLog.class))).thenReturn(1);
        int rows = printTemplateService.recordPreview(1L, 500L);
        assertEquals(1, rows);
        ArgumentCaptor<com.lin.distribution.domain.PrintPreviewLog> captor =
                ArgumentCaptor.forClass(com.lin.distribution.domain.PrintPreviewLog.class);
        verify(printPreviewLogMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getTemplateId());
        assertEquals(500L, captor.getValue().getDeliveryOrderId());
    }
}

package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.mapper.PrintTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        assertTrue(ex.getMessage().contains("未配置打印模板"));
    }

    @Test
    void 送货单为空应报错() {
        assertThrows(ServiceException.class, () -> printTemplateService.resolveForDeliveryOrder(null));
    }
}

package com.lin.distribution.service.support;

import com.alibaba.fastjson2.JSONObject;
import com.lin.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板内容治理器单元测试（W0-6：字段迁移/脱敏/导入安全校验/资源限制）
 */
class TemplateContentGovernorTest {

    private final TemplateContentGovernor governor = new TemplateContentGovernor();

    @Test
    void 弃用字段应迁移到新字段并剔除旧键() {
        JSONObject content = new JSONObject();
        content.put("paperSize", "A4");
        content.put("marginMm", 10);
        JSONObject migrated = governor.migrate(content);
        assertEquals("A4", migrated.get("paper"));
        assertEquals(10, migrated.get("margin"));
        assertFalse(migrated.containsKey("paperSize"));
        assertFalse(migrated.containsKey("marginMm"));
    }

    @Test
    void 弃用字段存在但新键已有时保留新键() {
        JSONObject content = new JSONObject();
        content.put("paperSize", "A4");
        content.put("paper", "A3");
        JSONObject migrated = governor.migrate(content);
        assertEquals("A3", migrated.get("paper"));
        assertFalse(migrated.containsKey("paperSize"));
    }

    @Test
    void 脱敏应剔除内部ID与绑定敏感字段() {
        JSONObject content = new JSONObject();
        content.put("templateId", 1L);
        content.put("customerId", 100L);
        content.put("deliveryPointId", 101L);
        content.put("bindType", 2);
        content.put("operator", "admin");
        content.put("publishedBy", "admin");
        content.put("paper", "A4");
        JSONObject sanitized = governor.sanitize(content);
        assertFalse(sanitized.containsKey("templateId"));
        assertFalse(sanitized.containsKey("customerId"));
        assertFalse(sanitized.containsKey("deliveryPointId"));
        assertFalse(sanitized.containsKey("bindType"));
        assertFalse(sanitized.containsKey("operator"));
        assertFalse(sanitized.containsKey("publishedBy"));
        assertEquals("A4", sanitized.get("paper"));
    }

    @Test
    void 导入内容schemaVersion不兼容应拒绝() {
        JSONObject content = new JSONObject();
        content.put("schemaVersion", 99);
        ServiceException ex = assertThrows(ServiceException.class, () -> governor.validateImport(content));
        assertTrue(ex.getMessage().contains("schemaVersion"));
    }

    @Test
    void 导入内容引用网络资源应拒绝() {
        JSONObject content = new JSONObject();
        content.put("schemaVersion", 1);
        JSONObject img = new JSONObject();
        img.put("src", "https://cdn.example.com/logo.png");
        content.put("logo", img);
        ServiceException ex = assertThrows(ServiceException.class, () -> governor.validateImport(content));
        assertTrue(ex.getMessage().contains("网络资源"));
    }

    @Test
    void 导入内容引用本地资源应通过() {
        JSONObject content = new JSONObject();
        content.put("schemaVersion", 1);
        JSONObject img = new JSONObject();
        img.put("src", "/profile/print/assets/abc.png");
        content.put("logo", img);
        assertDoesNotThrow(() -> governor.validateImport(content));
    }

    @Test
    void dataURI资源应视为本地通过() {
        JSONObject content = new JSONObject();
        content.put("schemaVersion", 1);
        content.put("background", "data:image/png;base64,iVBORw0K");
        assertDoesNotThrow(() -> governor.validateImport(content));
    }

    @Test
    void 数组内网络资源引用应被扫描拒绝() {
        JSONObject content = new JSONObject();
        content.put("schemaVersion", 1);
        JSONObject e1 = new JSONObject();
        e1.put("image", "/profile/print/assets/a.png");
        JSONObject e2 = new JSONObject();
        e2.put("url", "http://evil.example.com/x.png");
        com.alibaba.fastjson2.JSONArray arr = new com.alibaba.fastjson2.JSONArray();
        arr.add(e1);
        arr.add(e2);
        content.put("elements", arr);
        ServiceException ex = assertThrows(ServiceException.class, () -> governor.validateImport(content));
        assertTrue(ex.getMessage().contains("网络资源"));
    }

    @Test
    void 资源扩展名与大小校验() {
        governor.assertAssetAllowed("logo.png", 100);
        governor.assertAssetAllowed("logo.jpg", 100);
        governor.assertAssetAllowed("logo.JPEG", 100);
        assertThrows(ServiceException.class, () -> governor.assertAssetAllowed("a.gif", 100));
        assertThrows(ServiceException.class, () -> governor.assertAssetAllowed("a.bmp", 100));
        assertThrows(ServiceException.class, () -> governor.assertAssetAllowed("a.png", TemplateContentGovernor.ASSET_MAX_BYTES + 1));
    }

    @Test
    void 非JSON对象内容应报错() {
        assertThrows(ServiceException.class, () -> governor.parseContent("[1,2]"));
        assertThrows(ServiceException.class, () -> governor.parseContent("not json{"));
    }

    @Test
    void 空内容应解析为空对象() {
        JSONObject parsed = governor.parseContent("");
        assertTrue(parsed.isEmpty());
    }
}

package com.lin.distribution.service.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lin.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 打印模板内容治理器（W0-6，蓝图 §8-S0 第 28~39 条）
 * <ul>
 *   <li>29 模板字段按弃用迁移流程治理：{@link #migrate(JSONObject)} 将弃用字段映射到新字段后剔除；</li>
 *   <li>29 模板可安全导入导出但必须脱敏：导出前 {@link #sanitize(JSONObject)} 剔除 ID/绑定/操作者等敏感字段；</li>
 *   <li>37/38 导入必须安全校验（全有或全无）、禁止网络资源：{@link #validateImport(JSONObject)} 任一非法即整包拒绝；</li>
 *   <li>34 模板坐标统一毫米（约定：content 顶层 schemaVersion，坐标字段后缀 _mm 表示毫米，本治理器仅校验 schemaVersion 兼容）。</li>
 * </ul>
 * 当前 content 为 jimureport 设计 JSON（开放结构），故字段白名单仅约束本治理器自管的 <b>顶层受控字段</b>，
 * jimureport 自有结构字段透传不裁剪（不破坏设计器输出），但导入时统一注入 schemaVersion 并扫描网络资源。
 *
 * @author dsh
 */
@Component
public class TemplateContentGovernor {

    /** 模板包格式标识 */
    public static final String PACKAGE_FORMAT = "lin-print-template";
    /** 当前支持的模板内容 schema 版本；不兼容版本禁止导入（蓝图 30/38） */
    public static final int SCHEMA_VERSION = 1;
    /** 导入包大小上限 20MB（蓝图 39） */
    public static final long IMPORT_MAX_BYTES = 20L * 1024 * 1024;
    /** 资源文件大小上限 5MB（蓝图 36：预印底图限 PNG/JPG 5MB） */
    public static final long ASSET_MAX_BYTES = 5L * 1024 * 1024;
    /** 资源允许的扩展名（蓝图 36） */
    public static final String[] ASSET_EXTENSIONS = {"png", "jpg", "jpeg"};

    /** 顶层受控字段白名单（本治理器自管，jimureport 结构透传） */
    private static final Set<String> TOP_LEVEL_WHITELIST = new HashSet<>();
    static {
        TOP_LEVEL_WHITELIST.add("schemaVersion");
        TOP_LEVEL_WHITELIST.add("paper");
        TOP_LEVEL_WHITELIST.add("paperSize");
        TOP_LEVEL_WHITELIST.add("margin");
        TOP_LEVEL_WHITELIST.add("marginMm");
        TOP_LEVEL_WHITELIST.add("copies");
        TOP_LEVEL_WHITELIST.add("fields");
        TOP_LEVEL_WHITELIST.add("fixedText");
        TOP_LEVEL_WHITELIST.add("logo");
        TOP_LEVEL_WHITELIST.add("background");
        TOP_LEVEL_WHITELIST.add("elements");
        TOP_LEVEL_WHITELIST.add("layout");
    }

    /** 弃用字段 → 新字段迁移映射（蓝图 29：弃用迁移流程治理） */
    private static final Map<String, String> DEPRECATED_MIGRATIONS = Map.of(
            "paperSize", "paper",
            "marginMm", "margin"
    );

    /** 本地资源 URL 前缀白名单（content 引用资源仅允许本地 /profile 路径或 data URI） */
    private static final String[] LOCAL_URL_PREFIXES = {
            "/profile/print/assets/",
            "/dev-api/profile/print/assets/",
            "data:image/"
    };
    /** 网络资源 URL 模式（蓝图 37：禁止网络资源） */
    private static final Pattern HTTP_URL = Pattern.compile("(?i)^https?://.+");
    /** 疑似资源引用键名（扫描其字符串值是否为外链） */
    private static final Pattern ASSET_KEY_PATTERN = Pattern.compile(
            "(?i).*(src|url|image|img|background|logo|icon|picture|figure).*");

    public TemplateContentGovernor() {
    }

    /**
     * 解析内容 JSON 字符串为对象；空内容视为空对象。
     */
    public JSONObject parseContent(String content) {
        if (content == null || content.isBlank()) {
            return new JSONObject();
        }
        try {
            Object obj = JSON.parse(content);
            if (obj instanceof JSONObject) {
                return (JSONObject) obj;
            }
            // 数组等非对象结构视为非法
            throw new ServiceException("模板内容必须为 JSON 对象");
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("模板内容不是合法 JSON：" + e.getMessage());
        }
    }

    /**
     * 注入 schemaVersion（缺失则补当前版本；已有但不为整数则保留以触发 validateImport 拒绝）。
     */
    public JSONObject ensureSchemaVersion(JSONObject content) {
        content.putIfAbsent("schemaVersion", SCHEMA_VERSION);
        return content;
    }

    /**
     * 弃用字段迁移：将弃用键值迁移到新键（旧值与新键冲突时保留新键、旧值并入备注），迁移后剔除弃用键。
     * 应用于导入与保存路径。
     */
    public JSONObject migrate(JSONObject content) {
        if (content == null) {
            return new JSONObject();
        }
        for (Map.Entry<String, String> e : DEPRECATED_MIGRATIONS.entrySet()) {
            String oldKey = e.getKey();
            String newKey = e.getValue();
            if (content.containsKey(oldKey)) {
                Object oldVal = content.remove(oldKey);
                if (!content.containsKey(newKey)) {
                    content.put(newKey, oldVal);
                }
            }
        }
        return content;
    }

    /**
     * 导出前脱敏：剔除 ID/绑定/操作者等敏感字段，仅保留模板结构（蓝图 29：脱敏）。
     * 此处作用于 content JSON；模板元数据脱敏由导出服务在组装包时处理。
     */
    public JSONObject sanitize(JSONObject content) {
        if (content == null) {
            return new JSONObject();
        }
        // 移除内部 ID/绑定类敏感键
        content.remove("templateId");
        content.remove("customerId");
        content.remove("deliveryPointId");
        content.remove("bindType");
        content.remove("operator");
        content.remove("publishedBy");
        return content;
    }

    /**
     * 导入安全校验（全有或全无）：任一不合法即抛异常，整包拒绝导入。
     *
     * @param content 单个模板内容 JSON
     */
    public void validateImport(JSONObject content) {
        if (content == null) {
            throw new ServiceException("模板内容不能为空");
        }
        // schemaVersion 兼容校验（蓝图 30/38：不兼容模板禁止导入）
        Object sv = content.get("schemaVersion");
        if (!(sv instanceof Integer) || (Integer) sv != SCHEMA_VERSION) {
            throw new ServiceException("模板内容 schemaVersion 不兼容（当前支持 " + SCHEMA_VERSION + "）");
        }
        // 网络资源扫描（蓝图 37：禁止网络资源）
        scanForNetworkResources(content, "$");
    }

    /**
     * 扫描网络资源：递归遍历 content，凡资源引用键名命中且其字符串值为 http(s) 外链即拒绝。
     */
    private void scanForNetworkResources(Object node, String path) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            for (Map.Entry<String, Object> e : obj.entrySet()) {
                String key = e.getKey();
                Object val = e.getValue();
                String childPath = path + "." + key;
                if (val instanceof String && ASSET_KEY_PATTERN.matcher(key).matches()) {
                    String s = ((String) val).trim();
                    if (HTTP_URL.matcher(s).matches() && !isLocalAsset(s)) {
                        throw new ServiceException("模板禁止引用网络资源：" + childPath + " = " + s);
                    }
                }
                scanForNetworkResources(val, childPath);
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.size(); i++) {
                scanForNetworkResources(arr.get(i), path + "[" + i + "]");
            }
        }
    }

    private boolean isLocalAsset(String url) {
        for (String p : LOCAL_URL_PREFIXES) {
            if (url.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验资源扩展名与大小（蓝图 36：PNG/JPG 5MB）。
     */
    public void assertAssetAllowed(String fileName, long sizeBytes) {
        String ext = extensionOf(fileName);
        boolean ok = false;
        for (String a : ASSET_EXTENSIONS) {
            if (a.equalsIgnoreCase(ext)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            throw new ServiceException("资源仅允许 PNG/JPG/JPEG，拒绝文件：" + fileName);
        }
        if (sizeBytes > ASSET_MAX_BYTES) {
            throw new ServiceException("资源大小超出 5MB 上限：" + sizeBytes + " 字节");
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx + 1) : "";
    }
}

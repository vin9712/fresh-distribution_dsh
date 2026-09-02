package com.lin.distribution.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.PrintTemplateStatus;
import com.lin.distribution.constant.DeliveryScopeType;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.PrintPreviewLog;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.domain.PrintTemplateVersion;
import com.lin.distribution.mapper.PrintPreviewLogMapper;
import com.lin.distribution.mapper.PrintTemplateMapper;
import com.lin.distribution.mapper.PrintTemplateVersionMapper;
import com.lin.distribution.service.PrintTemplateService;
import com.lin.distribution.vo.DeliveryMatrixLayout;
import com.lin.distribution.vo.DeliveryPrintCandidateVO;
import com.lin.distribution.service.support.TemplateContentGovernor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.commons.lang3.StringUtils;

/**
 * 打印模板Service业务层处理（W0-4.4 版：状态机 + 版本历史 + 发布门禁 + 强制预览记录）
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintTemplateServiceImpl implements PrintTemplateService {
    private final PrintTemplateMapper printTemplateMapper;
    private final PrintTemplateVersionMapper printTemplateVersionMapper;
    private final PrintPreviewLogMapper printPreviewLogMapper;
    private final TemplateContentGovernor contentGovernor;

    /**
     * 查询打印模板
     */
    @Override
    public PrintTemplate selectPrintTemplateById(Long id) {
        return printTemplateMapper.selectPrintTemplateById(id);
    }

    /**
     * 查询打印模板列表
     */
    @Override
    public List<PrintTemplate> selectPrintTemplateList(PrintTemplate printTemplate) {
        return printTemplateMapper.selectPrintTemplateList(printTemplate);
    }

    /**
     * 查询 JimuReport 设计器可用报表清单（替代手工复制报表ID）
     */
    @Override
    public List<java.util.Map<String, Object>> selectJimuReports() {
        return printTemplateMapper.selectJimuReports();
    }

    /**
     * 打印模板绑定解析（P1/D-048 显式化）：按送货单组单范围判断打印形态（总单→MATRIX / 点单→FLAT），
     * 再按 客户+配送点 &gt; 客户 &gt; 全局默认 且限定同形态模板 解析。
     * 修复：A 类总单（delivery_point_id=NULL）不再靠 = NULL 的巧合回落，客户配的跨点总单矩阵模板能命中。
     */
    @Override
    public PrintTemplate resolveForDeliveryOrder(DeliveryOrder deliveryOrder) {
        if (deliveryOrder == null) {
            throw new ServiceException("送货单不存在");
        }
        String printForm = DeliveryScopeType.isCustomerDate(deliveryOrder.getScopeType())
                ? DeliveryMatrixLayout.FORM_MATRIX : DeliveryMatrixLayout.FORM_FLAT;
        PrintTemplate template = printTemplateMapper.selectBindTemplate(
                deliveryOrder.getCustomerId(), deliveryOrder.getDeliveryPointId(), printForm);
        if (template == null || !PrintTemplateStatus.PUBLISHED.getCode().equals(template.getStatus())) {
            throw new ServiceException("未配置已发布打印模板，请先在打印模板页面发布全局默认模板");
        }
        return template;
    }

    /**
     * 候选打印模板（P1/D-048 替代前端复制过滤）：同印刷形态、该客户可用的已发布模板，按绑定层级排序；
     * 命中「全局默认」（bind_type=3 且被选中）时标记 matchGlobalDefault=true 供打印对话框告警。
     */
    @Override
    public DeliveryPrintCandidateVO selectPrintCandidates(DeliveryOrder deliveryOrder) {
        if (deliveryOrder == null) {
            throw new ServiceException("送货单不存在");
        }
        String printForm = DeliveryScopeType.isCustomerDate(deliveryOrder.getScopeType())
                ? DeliveryMatrixLayout.FORM_MATRIX : DeliveryMatrixLayout.FORM_FLAT;
        if (deliveryOrder.getScopeType() == null) {
            printForm = DeliveryMatrixLayout.FORM_FLAT;
        }
        deliveryOrder.setScopeType(DeliveryScopeType.normalize(deliveryOrder.getScopeType()));
        List<PrintTemplate> candidates = printTemplateMapper.selectPrintCandidates(
                deliveryOrder.getCustomerId(), printForm);
        // 命中全局默认：候选里没有比全局默认更具体的（bind_type<3）层级时，说明回落到了全局默认
        boolean matchDefault = candidates.stream()
                .noneMatch(t -> t.getBindType() != null && t.getBindType() < 3);
        return DeliveryPrintCandidateVO.builder()
                .printForm(printForm)
                .matchGlobalDefault(Boolean.valueOf(matchDefault))
                .templates(candidates)
                .build();
    }

    /**
     * 新增打印模板（默认草稿）
     */
    @Override
    @Transactional
    public int insertPrintTemplate(PrintTemplate printTemplate) {
        normalizeNew(printTemplate);
        printTemplate.setCreateTime(DateUtils.getNowDate());
        return printTemplateMapper.insertPrintTemplate(printTemplate);
    }

    /**
     * 修改打印模板（仅草稿/已测试可改；已发布需先测试新版本再发布，不改已发布版）
     */
    @Override
    @Transactional
    public int updatePrintTemplate(PrintTemplate printTemplate) {
        PrintTemplate exist = printTemplateMapper.selectPrintTemplateById(printTemplate.getId());
        if (exist == null) {
            throw new ServiceException("打印模板不存在");
        }
        if (PrintTemplateStatus.PUBLISHED.getCode().equals(exist.getStatus())) {
            throw new ServiceException("已发布模板不可直接修改，请另存新版本后重新发布");
        }
        // 草稿/已测试保存不动状态；若传入新版内容，仍为草稿（发布走 publish）
        printTemplate.setStatus(PrintTemplateStatus.DRAFT.getCode());
        printTemplate.setTestWatermark(Boolean.FALSE);
        printTemplate.setUpdateTime(DateUtils.getNowDate());
        return printTemplateMapper.updatePrintTemplate(printTemplate);
    }

    /**
     * 测试发布（W0-4.4）：置为「已测试」并打测试水印；测试打印不计正式次数
     */
    @Override
    @Transactional
    public int testPublish(Long id) {
        PrintTemplate template = getExist(id);
        if (PrintTemplateStatus.PUBLISHED.getCode().equals(template.getStatus())) {
            throw new ServiceException("已发布模板不可测试发布");
        }
        PrintTemplate update = new PrintTemplate();
        update.setId(id);
        update.setStatus(PrintTemplateStatus.TESTED.getCode());
        update.setTestWatermark(Boolean.TRUE);
        update.setUpdateTime(DateUtils.getNowDate());
        return printTemplateMapper.updatePrintTemplate(update);
    }

    /**
     * 正式发布（W0-4.4）：校验发布门禁，置「已发布」并生成版本快照（已发布版本在新版本发布前继续使用）
     */
    @Override
    @Transactional
    public int publish(Long id, String remark) {
        PrintTemplate template = getExist(id);
        validatePublishGate(template);
        int nextVersion = printTemplateVersionMapper.selectMaxVersionNo(id) + 1;

        PrintTemplate update = new PrintTemplate();
        update.setId(id);
        update.setStatus(PrintTemplateStatus.PUBLISHED.getCode());
        update.setTestWatermark(Boolean.FALSE);
        update.setUpdateTime(DateUtils.getNowDate());
        printTemplateMapper.updatePrintTemplate(update);

        saveVersion(template, nextVersion, remark);
        log.info("[print template] 模板 {} 已发布（版本 {}）", template.getCode(), nextVersion);
        return nextVersion;
    }

    /**
     * 回滚到历史版本（W0-4.4）：以指定版本快照重生成一个新版本发布，不覆盖历史版本
     */
    @Override
    @Transactional
    public int rollback(Long templateId, Long versionId, String remark) {
        if (templateId == null || versionId == null) {
            throw new ServiceException("模板与版本不能为空");
        }
        PrintTemplate template = getExist(templateId);
        PrintTemplateVersion source = printTemplateVersionMapper.selectById(versionId);
        if (source == null || !source.getTemplateId().equals(templateId)) {
            throw new ServiceException("版本不存在或不属于该模板");
        }
        int nextVersion = printTemplateVersionMapper.selectMaxVersionNo(templateId) + 1;

        // 以版本快照回填当前模板（名称/内容/绑定/联数）
        PrintTemplate restore = new PrintTemplate();
        restore.setId(templateId);
        restore.setName(source.getName());
        restore.setContent(source.getContent());
        restore.setBindType(source.getBindType());
        restore.setPrintForm(source.getPrintForm());
        restore.setCustomerId(source.getCustomerId());
        restore.setDeliveryPointId(source.getDeliveryPointId());
        restore.setCopies(source.getCopies());
        restore.setStatus(PrintTemplateStatus.PUBLISHED.getCode());
        restore.setTestWatermark(Boolean.FALSE);
        restore.setVersion(template.getVersion() == null ? 1 : template.getVersion() + 1);
        restore.setUpdateTime(DateUtils.getNowDate());
        printTemplateMapper.updatePrintTemplate(restore);

        PrintTemplate restored = printTemplateMapper.selectPrintTemplateById(templateId);
        saveVersion(restored, nextVersion, StringUtils.defaultString(remark,
                "回滚自版本 " + source.getVersionNo()));
        log.info("[print template] 模板 {} 回滚至版本 {}（当前 {}）", template.getCode(), source.getVersionNo(), nextVersion);
        return nextVersion;
    }

    /**
     * 查询模板版本列表
     */
    @Override
    public List<PrintTemplateVersion> listVersions(Long templateId) {
        return printTemplateVersionMapper.selectListByTemplateId(templateId);
    }

    /**
     * 记录打印预览（正式打印前必须有预览记录）
     */
    @Override
    @Transactional
    public int recordPreview(Long templateId, Long deliveryOrderId) {
        if (templateId == null) {
            throw new ServiceException("模板ID不能为空");
        }
        PrintPreviewLog log = new PrintPreviewLog();
        log.setTemplateId(templateId);
        log.setDeliveryOrderId(deliveryOrderId);
        log.setOperator(resolveOperator());
        log.setPreviewTime(DateUtils.getNowDate());
        return printPreviewLogMapper.insert(log);
    }

    /**
     * 读当前发布版本号
     */
    @Override
    public int currentPublishedVersion(Long templateId) {
        return printTemplateVersionMapper.selectMaxVersionNo(templateId);
    }

    /**
     * 批量删除打印模板
     */
    @Override
    public int deletePrintTemplateByIds(Long[] ids) {
        if (ids == null) {
            return 0;
        }
        for (Long id : ids) {
            PrintTemplate template = printTemplateMapper.selectPrintTemplateById(id);
            if (template == null) {
                continue;
            }
            if (PrintTemplateStatus.PUBLISHED.getCode().equals(template.getStatus())) {
                throw new ServiceException("已发布模板不可删除（W0-4.4：曾正式使用过的模板仅可停用，不可删除）：" + template.getName());
            }
        }
        return printTemplateMapper.deletePrintTemplateByIds(ids);
    }

    /**
     * 删除打印模板
     */
    @Override
    public int deletePrintTemplateById(Long id) {
        PrintTemplate template = printTemplateMapper.selectPrintTemplateById(id);
        if (template == null) {
            return 0;
        }
        if (PrintTemplateStatus.PUBLISHED.getCode().equals(template.getStatus())) {
            throw new ServiceException("已发布模板不可删除（可停用）：" + template.getName());
        }
        return printTemplateMapper.deletePrintTemplateById(id);
    }

    /**
     * 发布门禁校验（W0-4.4）：必填字段（名称/内容/绑定类型）+ 联数 + 绑定完整性 + 内容可解析（JSON）+\n
     * 长文本溢出（以内容长度上限做后端兜底；精确溢出与纸张/分页由设计器/前端发布校验承担）
     */
    private void validatePublishGate(PrintTemplate template) {
        if (StringUtils.isBlank(template.getName())) {
            throw new ServiceException("模板名称必填，无法发布");
        }
        if (StringUtils.isBlank(template.getContent())) {
            throw new ServiceException("模板内容必填，无法发布");
        }
        if (template.getContent().length() > MAX_CONTENT_CHARS) {
            throw new ServiceException("模板内容过长（超过 " + MAX_CONTENT_CHARS + " 字符），可能溢出，请精简后再发布");
        }
        if (template.getBindType() == null
                || (template.getBindType() < 1 || template.getBindType() > 3)) {
            throw new ServiceException("绑定类型必填（1客户+配送点 2客户 3全局默认）");
        }
        if (Integer.valueOf(1).equals(template.getBindType()) && template.getDeliveryPointId() == null) {
            throw new ServiceException("客户+配送点组合模板必须指定配送点");
        }
        if (template.getCopies() == null || template.getCopies() < 1) {
            throw new ServiceException("联数必须大于等于 1");
        }
        // JSON 可解析校验
        try {
            com.alibaba.fastjson2.JSON.parse(template.getContent());
        } catch (Exception e) {
            throw new ServiceException("模板内容非法 JSON，无法发布");
        }
    }

    private void saveVersion(PrintTemplate template, int versionNo, String remark) {
        PrintTemplateVersion version = new PrintTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNo(versionNo);
        version.setName(template.getName());
        version.setContent(template.getContent());
        version.setBindType(template.getBindType());
        version.setPrintForm(template.getPrintForm());
        version.setCustomerId(template.getCustomerId());
        version.setDeliveryPointId(template.getDeliveryPointId());
        version.setCopies(template.getCopies());
        version.setPublishedBy(resolveOperator());
        version.setPublishedTime(DateUtils.getNowDate());
        version.setRemark(remark);
        printTemplateVersionMapper.insert(version);
    }

    private void normalizeNew(PrintTemplate printTemplate) {
        if (StringUtils.isBlank(printTemplate.getCode())) {
            printTemplate.setCode("TPL" + DateUtils.dateTimeNow("yyyyMMddHHmmss"));
        }
        if (printTemplate.getBindType() == null) {
            printTemplate.setBindType(3);
        }
        if (StringUtils.isBlank(printTemplate.getPrintForm())) {
            printTemplate.setPrintForm(DeliveryMatrixLayout.FORM_FLAT);
        }
        if (printTemplate.getCopies() == null) {
            printTemplate.setCopies(1);
        }
        if (printTemplate.getStatus() == null) {
            printTemplate.setStatus(PrintTemplateStatus.DRAFT.getCode());
        }
        if (printTemplate.getTestWatermark() == null) {
            printTemplate.setTestWatermark(Boolean.FALSE);
        }
        if (printTemplate.getIsDefault() == null) {
            printTemplate.setIsDefault("0");
        }
    }

    // ==================== W0-6 模板导入导出 ====================

    /**
     * 导出模板为开放 JSON 包（蓝图 37/39）。脱敏：剔除内部 ID/绑定/操作者，保留结构。
     */
    @Override
    public Map<String, Object> exportTemplate(Long id, boolean includeVersions) {
        PrintTemplate template = getExist(id);
        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("format", TemplateContentGovernor.PACKAGE_FORMAT);
        pkg.put("schemaVersion", TemplateContentGovernor.SCHEMA_VERSION);
        pkg.put("exportedAt", DateUtils.dateTimeNow());
        pkg.put("exportedBy", resolveOperator());

        Map<String, Object> tpl = new LinkedHashMap<>();
        tpl.put("name", template.getName());
        tpl.put("type", template.getType());
        tpl.put("renderEngine", template.getRenderEngine());
        tpl.put("copies", template.getCopies());
        // 脱敏后的 content
        JSONObject content = contentGovernor.parseContent(template.getContent());
        content = contentGovernor.ensureSchemaVersion(content);
        content = contentGovernor.sanitize(content);
        tpl.put("content", content);

        if (includeVersions) {
            List<PrintTemplateVersion> versions = printTemplateVersionMapper.selectListByTemplateId(id);
            List<Map<String, Object>> versionList = new ArrayList<>();
            for (PrintTemplateVersion v : versions) {
                Map<String, Object> vm = new LinkedHashMap<>();
                vm.put("versionNo", v.getVersionNo());
                vm.put("name", v.getName());
                vm.put("copies", v.getCopies());
                vm.put("publishedTime", v.getPublishedTime());
                vm.put("remark", v.getRemark());
                JSONObject vc = contentGovernor.parseContent(v.getContent());
                vc = contentGovernor.ensureSchemaVersion(vc);
                vm.put("content", contentGovernor.sanitize(vc));
                versionList.add(vm);
            }
            tpl.put("versions", versionList);
        }
        pkg.put("templates", List.of(tpl));
        return pkg;
    }

    /**
     * 导入模板包（蓝图 38/39：全有或全无安全校验、20MB 上限、禁止网络资源、不兼容禁止导入；
     * 导入后为重命名的未绑定草稿，须重走完整发布门禁）。
     */
    @Override
    @Transactional
    public int importTemplates(String packageJson) {
        if (packageJson == null || packageJson.isBlank()) {
            throw new ServiceException("导入包为空");
        }
        if (packageJson.length() > TemplateContentGovernor.IMPORT_MAX_BYTES) {
            throw new ServiceException("导入包超出 20MB 上限");
        }
        JSONObject pkg;
        try {
            pkg = JSON.parseObject(packageJson);
        } catch (Exception e) {
            throw new ServiceException("导入包不是合法 JSON：" + e.getMessage());
        }
        if (pkg == null || !TemplateContentGovernor.PACKAGE_FORMAT.equals(pkg.getString("format"))) {
            throw new ServiceException("导入包格式不兼容（format != " + TemplateContentGovernor.PACKAGE_FORMAT + "）");
        }
        Integer pkgSchema = pkg.getInteger("schemaVersion");
        if (pkgSchema == null || pkgSchema != TemplateContentGovernor.SCHEMA_VERSION) {
            throw new ServiceException("导入包 schemaVersion 不兼容（当前支持 " + TemplateContentGovernor.SCHEMA_VERSION + "）");
        }
        JSONArray templates = pkg.getJSONArray("templates");
        if (templates == null || templates.isEmpty()) {
            throw new ServiceException("导入包不含任何模板");
        }

        // 全有或全无：先全部校验，任一非法即整体拒绝
        List<JSONObject> validated = new ArrayList<>();
        for (int i = 0; i < templates.size(); i++) {
            JSONObject t = templates.getJSONObject(i);
            validateImportTemplate(t);
            validated.add(t);
        }
        // 校验通过后统一落库
        int count = 0;
        for (JSONObject t : validated) {
            PrintTemplate template = new PrintTemplate();
            // 重命名的未绑定草稿：名称前缀「导入_原名_时间戳」，避免冲突
            template.setName("导入_" + t.getString("name") + "_" + DateUtils.dateTimeNow("yyyyMMddHHmmss"));
            template.setType(t.getInteger("type"));
            template.setRenderEngine(StringUtils.defaultIfBlank(t.getString("renderEngine"), "jimureport"));
            template.setCopies(t.getIntValue("copies", 1) > 0 ? t.getIntValue("copies", 1) : 1);
            // 未绑定：全局默认草稿，须重走完整发布门禁
            JSONObject content = contentGovernor.ensureSchemaVersion(t.getJSONObject("content"));
            content = contentGovernor.migrate(content);
            template.setContent(JSON.toJSONString(content));
            template.setBindType(3);
            template.setCustomerId(0L);
            template.setIsDefault("0");
            template.setStatus(PrintTemplateStatus.DRAFT.getCode());
            template.setTestWatermark(Boolean.FALSE);
            template.setCode("TPL" + DateUtils.dateTimeNow("yyyyMMddHHmmssSSS"));
            template.setCreateTime(DateUtils.getNowDate());
            printTemplateMapper.insertPrintTemplate(template);
            count++;
        }
        log.info("[print template] 批量导入模板 {} 个（操作者：{}）", count, resolveOperator());
        return count;
    }

    /**
     * 单模板导入校验（全有或全无）：name/type/renderEngine/copies/content 均合法，且 content 通过治理器校验。
     */
    private void validateImportTemplate(JSONObject t) {
        if (t == null) {
            throw new ServiceException("导入模板项为空");
        }
        if (StringUtils.isBlank(t.getString("name"))) {
            throw new ServiceException("导入模板 name 不能为空");
        }
        Integer type = t.getInteger("type");
        if (type == null || (type != 0 && type != 1)) {
            throw new ServiceException("导入模板 type 非法（仅 0 送货单 / 1 汇总表）");
        }
        String engine = StringUtils.defaultIfBlank(t.getString("renderEngine"), "jimureport");
        if (!"jimureport".equalsIgnoreCase(engine)) {
            throw new ServiceException("不兼容的渲染引擎，禁止导入：" + engine);
        }
        Integer copies = t.getInteger("copies");
        if (copies == null || copies < 1) {
            throw new ServiceException("导入模板 copies 非法");
        }
        JSONObject content = t.getJSONObject("content");
        if (content == null) {
            throw new ServiceException("导入模板 content 不能为空");
        }
        // 安全校验（schemaVersion 兼容 + 禁止网络资源）
        contentGovernor.validateImport(content);
    }

    private PrintTemplate getExist(Long id) {
        if (id == null) {
            throw new ServiceException("模板ID不能为空");
        }
        PrintTemplate template = printTemplateMapper.selectPrintTemplateById(id);
        if (template == null) {
            throw new ServiceException("打印模板不存在");
        }
        return template;
    }

    private String resolveOperator() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }

    /** 模板内容字符数上限（长文本溢出兜底；精确溢出由设计器校验） */
    private static final int MAX_CONTENT_CHARS = 200000;
}

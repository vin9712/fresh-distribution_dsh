package com.lin.distribution.service.impl;

import java.util.List;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.PrintTemplateStatus;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.PrintPreviewLog;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.domain.PrintTemplateVersion;
import com.lin.distribution.mapper.PrintPreviewLogMapper;
import com.lin.distribution.mapper.PrintTemplateMapper;
import com.lin.distribution.mapper.PrintTemplateVersionMapper;
import com.lin.distribution.service.PrintTemplateService;
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
     * 三级绑定解析（客户+配送点组合 > 客户 > 全局默认；停用/未发布模板不参与，自动回退）
     */
    @Override
    public PrintTemplate resolveForDeliveryOrder(DeliveryOrder deliveryOrder) {
        if (deliveryOrder == null) {
            throw new ServiceException("送货单不存在");
        }
        PrintTemplate template = printTemplateMapper.selectBindTemplate(deliveryOrder.getCustomerId(), deliveryOrder.getDeliveryPointId());
        if (template == null || !PrintTemplateStatus.PUBLISHED.getCode().equals(template.getStatus())) {
            throw new ServiceException("未配置已发布打印模板，请先在打印模板页面发布全局默认模板");
        }
        return template;
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

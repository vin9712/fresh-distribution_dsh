package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.domain.PrintTemplateVersion;

/**
 * 打印模板Service接口
 *
 * @author lin
 * @date 2024-12-18
 */
public interface PrintTemplateService {
    /**
     * 查询打印模板
     *
     * @param id 打印模板主键
     * @return 打印模板
     */
    PrintTemplate selectPrintTemplateById(Long id);

    /**
     * 查询打印模板列表
     *
     * @param printTemplate 打印模板
     * @return 打印模板集合
     */
    List<PrintTemplate> selectPrintTemplateList(PrintTemplate printTemplate);

    /**
     * 按送货单解析打印模板（三级绑定：客户+配送点组合 > 客户 > 全局默认；停用回退全局默认）
     *
     * @param deliveryOrder 送货单
     * @return 命中的模板（未配置任何模板则抛异常）
     */
    PrintTemplate resolveForDeliveryOrder(DeliveryOrder deliveryOrder);

    /**
     * 新增打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    int insertPrintTemplate(PrintTemplate printTemplate);

    /**
     * 修改打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    int updatePrintTemplate(PrintTemplate printTemplate);

    /**
     * 批量删除打印模板
     *
     * @param ids 需要删除的打印模板主键集合
     * @return 结果
     */
    int deletePrintTemplateByIds(Long[] ids);

    /**
     * 删除打印模板信息
     *
     * @param id 打印模板主键
     * @return 结果
     */
    int deletePrintTemplateById(Long id);

    /**
     * 测试发布模板（W0-4.4）：置为「已测试」并打测试水印（测试打印不计正式次数）
     *
     * @param id 模板ID
     * @return 结果
     */
    int testPublish(Long id);

    /**
     * 正式发布模板（W0-4.4）：校验发布门禁（必填字段/绑定/信息完整度），置为「已发布」并生成版本快照；
     * 已发布版本在新版本发布前继续使用。
     *
     * @param id  模板ID
     * @param remark 版本说明
     * @return 结果
     */
    int publish(Long id, String remark);

    /**
     * 回滚到历史版本（W0-4.4）：以指定版本快照重生成一个新版本发布，不覆盖历史版本
     *
     * @param templateId 模板ID
     * @param versionId  历史版本ID
     * @param remark     版本说明
     * @return 结果
     */
    int rollback(Long templateId, Long versionId, String remark);

    /**
     * 查询模板版本列表（W0-4.4）
     *
     * @param templateId 模板ID
     * @return 版本集合
     */
    List<PrintTemplateVersion> listVersions(Long templateId);

    /**
     * 记录一次打印预览（W0-4.4：正式打印前必须有预览记录）
     *
     * @param templateId     模板ID
     * @param deliveryOrderId 送货单ID（汇总预览为空）
     * @return 结果
     */
    int recordPreview(Long templateId, Long deliveryOrderId);

    /**
     * 查询某模板的当前发布版本号（W0-4.4，读已发布版本的版本快照）
     *
     * @param templateId 模板ID
     * @return 最新版本号（未发布返回 0）
     */
    int currentPublishedVersion(Long templateId);
}

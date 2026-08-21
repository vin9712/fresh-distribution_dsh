package com.lin.distribution.service;

import com.lin.distribution.domain.DefaultSkuTemplate;
import com.lin.distribution.domain.DefaultSkuTemplateItem;

import java.util.List;

/**
 * 默认SKU模板Service接口（批量赋值默认SKU，不含价格）
 *
 * @author dsh
 */
public interface DefaultSkuTemplateService {

    DefaultSkuTemplate selectDefaultSkuTemplateById(Long id);

    List<DefaultSkuTemplate> selectDefaultSkuTemplateList(DefaultSkuTemplate defaultSkuTemplate);

    /**
     * 查询模板明细（SKU集合）
     */
    List<DefaultSkuTemplateItem> selectTemplateItems(Long templateId);

    /**
     * 新增模板（含模板明细 SKU 集合）
     */
    int insertDefaultSkuTemplate(DefaultSkuTemplate defaultSkuTemplate, List<Long> skuIds);

    /**
     * 修改模板（替换明细 SKU 集合）
     */
    int updateDefaultSkuTemplate(DefaultSkuTemplate defaultSkuTemplate, List<Long> skuIds);

    int deleteDefaultSkuTemplateByIds(Long[] ids);

    /**
     * 批量赋值默认SKU到目标客户（deepseek_redesign.md §5.1）
     *
     * @param skuIds      标准SKU集合（与模板二选一）
     * @param customerIds 目标客户ID集合
     * @param strategy    覆盖策略：1=仅新增，2=覆盖未个性化，3=全部覆盖（慎用）
     * @param templateId  来源模板ID（可空）
     * @return 新增/更新条数
     */
    int batchAssign(List<Long> skuIds, List<Long> customerIds, int strategy, Long templateId);
}

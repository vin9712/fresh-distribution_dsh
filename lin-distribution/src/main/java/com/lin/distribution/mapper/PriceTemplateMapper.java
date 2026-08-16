package com.lin.distribution.mapper;

import com.lin.distribution.domain.PriceTemplate;

import java.util.List;

/**
 * 报价模板 Mapper
 *
 * @author dsh
 */
public interface PriceTemplateMapper {

    PriceTemplate selectPriceTemplateById(Long id);

    List<PriceTemplate> selectPriceTemplateList(PriceTemplate priceTemplate);

    int insertPriceTemplate(PriceTemplate priceTemplate);

    int updatePriceTemplate(PriceTemplate priceTemplate);

    int deletePriceTemplateByIds(Long[] ids);

    /** 查询启用的全局默认模板 */
    PriceTemplate selectDefaultTemplate();

    /** 查询客户绑定的模板（price_template_customer 关联） */
    PriceTemplate selectTemplateByCustomerId(Long customerId);
}

package com.lin.distribution.mapper;

import com.lin.distribution.domain.PriceTemplateSku;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 报价模板SKU价格 Mapper
 *
 * @author dsh
 */
public interface PriceTemplateSkuMapper {

    PriceTemplateSku selectPriceTemplateSkuById(Long id);

    List<PriceTemplateSku> selectPriceTemplateSkuList(@Param("templateId") Long templateId);

    int insertPriceTemplateSku(PriceTemplateSku priceTemplateSku);

    int updatePriceTemplateSku(PriceTemplateSku priceTemplateSku);

    int deletePriceTemplateSkuByIds(Long[] ids);

    int deletePriceTemplateSkuByTemplateId(Long templateId);

    /** 取价：模板SKU在有效期区间内的最新价格 */
    PriceTemplateSku selectActivePriceByTemplateAndSku(@Param("templateId") Long templateId,
                                                       @Param("skuId") Long skuId,
                                                       @Param("priceDate") LocalDate priceDate);
}

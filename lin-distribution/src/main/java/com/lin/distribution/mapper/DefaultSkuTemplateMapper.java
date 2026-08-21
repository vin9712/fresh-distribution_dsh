package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.DefaultSkuTemplate;

/**
 * 默认SKU模板Mapper接口
 *
 * @author dsh
 */
public interface DefaultSkuTemplateMapper {

    DefaultSkuTemplate selectDefaultSkuTemplateById(Long id);

    List<DefaultSkuTemplate> selectDefaultSkuTemplateList(DefaultSkuTemplate defaultSkuTemplate);

    int insertDefaultSkuTemplate(DefaultSkuTemplate defaultSkuTemplate);

    int updateDefaultSkuTemplate(DefaultSkuTemplate defaultSkuTemplate);

    int deleteDefaultSkuTemplateById(Long id);

    int deleteDefaultSkuTemplateByIds(Long[] ids);
}

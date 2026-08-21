package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.DefaultSkuTemplateItem;
import org.apache.ibatis.annotations.Param;

/**
 * 默认SKU模板明细Mapper接口
 *
 * @author dsh
 */
public interface DefaultSkuTemplateItemMapper {

    List<DefaultSkuTemplateItem> selectByTemplateId(@Param("templateId") Long templateId);

    int insertBatch(@Param("items") List<DefaultSkuTemplateItem> items);

    int deleteByTemplateId(@Param("templateId") Long templateId);
}

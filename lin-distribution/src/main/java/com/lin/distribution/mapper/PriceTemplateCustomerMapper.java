package com.lin.distribution.mapper;

import com.lin.distribution.domain.PriceTemplateCustomer;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 报价模板-客户绑定 Mapper（一个客户最多绑定一个模板）
 *
 * @author dsh
 */
public interface PriceTemplateCustomerMapper {

    List<PriceTemplateCustomer> selectByTemplateId(@Param("templateId") Long templateId);

    PriceTemplateCustomer selectByCustomerId(@Param("customerId") Long customerId);

    int insert(PriceTemplateCustomer record);

    int deleteByTemplateIdAndCustomerId(@Param("templateId") Long templateId, @Param("customerId") Long customerId);

    int deleteByTemplateId(Long templateId);
}

package com.lin.distribution.mapper;

import com.lin.distribution.domain.CustomerSkuMapping;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户SKU映射Mapper接口
 *
 * @author lin
 * @date 2024-11-20
 */
public interface CustomerSkuMappingMapper {
    /**
     * 查询客户SKU映射
     *
     * @param id 客户SKU映射主键
     * @return 客户SKU映射
     */
    CustomerSkuMapping selectCustomerSkuMappingById(Long id);

    /**
     * 查询客户SKU映射列表
     *
     * @param customerSkuMapping 客户SKU映射
     * @return 客户SKU映射集合
     */
    List<CustomerSkuMapping> selectCustomerSkuMappingList(CustomerSkuMapping customerSkuMapping);

    /**
     * 按 (customer_id, customer_alias) 精确查重
     *
     * @param customerId    客户ID
     * @param customerAlias 客户侧叫法
     * @return 客户SKU映射集合
     */
    List<CustomerSkuMapping> selectCustomerSkuMappingByCustomerIdAndAlias(@Param("customerId") Long customerId, @Param("customerAlias") String customerAlias);

    /**
     * 新增客户SKU映射
     *
     * @param customerSkuMapping 客户SKU映射
     * @return 结果
     */
    int insertCustomerSkuMapping(CustomerSkuMapping customerSkuMapping);

    /**
     * 修改客户SKU映射
     *
     * @param customerSkuMapping 客户SKU映射
     * @return 结果
     */
    int updateCustomerSkuMapping(CustomerSkuMapping customerSkuMapping);

    /**
     * 批量删除客户SKU映射
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteCustomerSkuMappingByIds(Long[] ids);
}

package com.lin.distribution.service;

import com.lin.distribution.domain.CustomerSkuMapping;

import java.util.List;

/**
 * 客户SKU映射Service接口
 *
 * @author lin
 * @date 2024-11-20
 */
public interface CustomerSkuMappingService {
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
     * @param ids 需要删除的客户SKU映射主键集合
     * @return 结果
     */
    int deleteCustomerSkuMappingByIds(Long[] ids);
}

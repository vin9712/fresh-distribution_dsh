package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.domain.CustomerSkuMapping;
import com.lin.distribution.mapper.CustomerSkuMappingMapper;
import com.lin.distribution.service.CustomerSkuMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客户SKU映射Service业务层处理
 *
 * @author lin
 * @date 2024-11-20
 */
@Service
public class CustomerSkuMappingServiceImpl implements CustomerSkuMappingService
{
    @Autowired
    private CustomerSkuMappingMapper customerSkuMappingMapper;

    /**
     * 查询客户SKU映射
     *
     * @param id 客户SKU映射主键
     * @return 客户SKU映射
     */
    @Override
    public CustomerSkuMapping selectCustomerSkuMappingById(Long id)
    {
        return customerSkuMappingMapper.selectCustomerSkuMappingById(id);
    }

    /**
     * 查询客户SKU映射列表
     *
     * @param customerSkuMapping 客户SKU映射
     * @return 客户SKU映射
     */
    @Override
    public List<CustomerSkuMapping> selectCustomerSkuMappingList(CustomerSkuMapping customerSkuMapping)
    {
        return customerSkuMappingMapper.selectCustomerSkuMappingList(customerSkuMapping);
    }

    /**
     * 新增客户SKU映射
     *
     * @param customerSkuMapping 客户SKU映射
     * @return 结果
     */
    @Override
    public int insertCustomerSkuMapping(CustomerSkuMapping customerSkuMapping)
    {
        // check unique mapping
        checkUniqueMapping(customerSkuMapping);

        customerSkuMapping.setCreateTime(DateUtils.getNowDate());
        return customerSkuMappingMapper.insertCustomerSkuMapping(customerSkuMapping);
    }

    /**
     * 修改客户SKU映射
     *
     * @param customerSkuMapping 客户SKU映射
     * @return 结果
     */
    @Override
    public int updateCustomerSkuMapping(CustomerSkuMapping customerSkuMapping)
    {
        // check unique mapping
        checkUniqueMapping(customerSkuMapping);

        customerSkuMapping.setUpdateTime(DateUtils.getNowDate());
        return customerSkuMappingMapper.updateCustomerSkuMapping(customerSkuMapping);
    }

    /**
     * 批量删除客户SKU映射
     *
     * @param ids 需要删除的客户SKU映射主键
     * @return 结果
     */
    @Override
    public int deleteCustomerSkuMappingByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return customerSkuMappingMapper.deleteCustomerSkuMappingByIds(ids);
    }

    private void checkUniqueMapping(CustomerSkuMapping customerSkuMapping) {
        if (customerSkuMapping == null) {
            throw new ServiceException("customer sku mapping is null");
        }
        if (customerSkuMapping.getCustomerId() == null) {
            throw new ServiceException("客户不能为空");
        }
        if (customerSkuMapping.getCustomerAlias() == null || customerSkuMapping.getCustomerAlias().trim().isEmpty()) {
            throw new ServiceException("客户叫法不能为空");
        }

        List<CustomerSkuMapping> mappings = customerSkuMappingMapper.selectCustomerSkuMappingByCustomerIdAndAlias(
                customerSkuMapping.getCustomerId(), customerSkuMapping.getCustomerAlias());

        long count = 0;
        if (customerSkuMapping.getId() != null) {
            count = mappings.stream()
                    .filter(item -> !item.getId().equals(customerSkuMapping.getId()))
                    .count();
        } else {
            count = mappings.size();
        }

        if (count > 0) {
            throw new ServiceException("客户叫法已存在");
        }
    }
}

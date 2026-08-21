package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.CustomerGroup;

/**
 * 客户分组Mapper接口
 *
 * @author dsh
 */
public interface CustomerGroupMapper {

    CustomerGroup selectCustomerGroupById(Long id);

    List<CustomerGroup> selectCustomerGroupList(CustomerGroup customerGroup);

    int insertCustomerGroup(CustomerGroup customerGroup);

    int updateCustomerGroup(CustomerGroup customerGroup);

    int deleteCustomerGroupById(Long id);

    int deleteCustomerGroupByIds(Long[] ids);
}

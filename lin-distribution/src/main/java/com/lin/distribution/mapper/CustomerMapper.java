package com.lin.distribution.mapper;

import com.lin.distribution.domain.Customer;

import java.util.List;

/**
 * 客户Mapper接口
 *
 * @author lin
 * @date 2024-11-08
 */
public interface CustomerMapper {
    /**
     * 查询客户
     *
     * @param id 客户主键
     * @return 客户
     */
    Customer selectCustomerById(Long id);

    /**
     * 查询客户列表
     *
     * @param customer 客户
     * @return 客户集合
     */
    List<Customer> selectCustomerList(Customer customer);

    /**
     * 新增客户
     *
     * @param customer 客户
     * @return 结果
     */
    int insertCustomer(Customer customer);

    /**
     * 修改客户
     *
     * @param customer 客户
     * @return 结果
     */
    int updateCustomer(Customer customer);

    /**
     * 删除客户
     *
     * @param id 客户主键
     * @return 结果
     */
    int deleteCustomerById(Long id);

    /**
     * 批量删除客户
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteCustomerByIds(Long[] ids);

    List<Customer> selectCustomerByName(String name);
}

package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.domain.Customer;

/**
 * 客户Service接口
 *
 * @author lin
 * @date 2024-11-08
 */
public interface CustomerService {
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
     * 批量删除客户
     *
     * @param ids 需要删除的客户主键集合
     * @return 结果
     */
    int deleteCustomerByIds(Long[] ids);

    /**
     * 删除客户信息
     *
     * @param id 客户主键
     * @return 结果
     */
    int deleteCustomerById(Long id);
}

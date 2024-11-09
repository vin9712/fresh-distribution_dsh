package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.PinYinConvertUtils;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.service.CustomerDeptService;
import com.lin.distribution.service.CustomerService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客户Service业务层处理
 *
 * @author lin
 * @date 2024-11-08
 */
@Service
public class CustomerServiceImpl implements CustomerService {

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private CustomerDeptService customerDeptService;

    /**
     * 查询客户
     *
     * @param id 客户主键
     * @return 客户
     */
    @Override
    public Customer selectCustomerById(Long id) {
        return customerMapper.selectCustomerById(id);
    }

    /**
     * 查询客户列表
     *
     * @param customer 客户
     * @return 客户
     */
    @Override
    public List<Customer> selectCustomerList(Customer customer) {
        return customerMapper.selectCustomerList(customer);
    }

    /**
     * 新增客户
     *
     * @param customer 客户
     * @return 结果
     */
    @Override
    public int insertCustomer(Customer customer) {
        // check unique customer name
        checkUniqueCustomer(customer);

        customer.setCreateTime(DateUtils.getNowDate());
        int insert = customerMapper.insertCustomer(customer);
        Long customerId = customer.getId();

        // add a default customer dept
        String customerDeptName =  StringUtils.isNotEmpty(customer.getAlias()) ? customer.getAlias() : customer.getName();
        CustomerDept customerDept = CustomerDept.builder()
                .customerId(customerId)
                .parentId(0L)
                .name(customerDeptName)
                .mnemonicCode(PinYinConvertUtils.toFirstChar(customerDeptName))
                .isDeleted(Boolean.FALSE)
                .build();
        customerDeptService.insertCustomerDept(customerDept);
        return insert;
    }

    /**
     * 修改客户
     *
     * @param customer 客户
     * @return 结果
     */
    @Override
    public int updateCustomer(Customer customer) {
        // check unique customer name
        checkUniqueCustomer(customer);

        customer.setUpdateTime(DateUtils.getNowDate());
        return customerMapper.updateCustomer(customer);
    }

    /**
     * 批量删除客户
     *
     * @param ids 需要删除的客户主键
     * @return 结果
     */
    @Override
    public int deleteCustomerByIds(Long[] ids) {
        return customerMapper.deleteCustomerByIds(ids);
    }

    /**
     * 删除客户信息
     *
     * @param id 客户主键
     * @return 结果
     */
    @Override
    public int deleteCustomerById(Long id) {
        return customerMapper.deleteCustomerById(id);
    }

    private void checkUniqueCustomer(Customer customer) {
        if (customer == null) {
            throw new ServiceException("customer is null");
        }

        List<Customer> customerList = customerMapper.selectCustomerByName(customer.getName());

        long count = 0;
        if (customer.getId() != null) {
            count = customerList.stream()
                    .filter(item -> !item.getId().equals(customer.getId()))
                    .count();
        } else {
            count = customerList.size();
        }

        if (count > 0) {
            throw new ServiceException("customer name is exist");
        }
    }
}

package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.service.CustomerDeptService;
import com.lin.distribution.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 客户Service业务层处理
 *
 * @author lin
 * @date 2024-11-08
 */
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper customerMapper;
    private final CustomerDeptMapper customerDeptMapper;
    private final CustomerDeptService customerDeptService;

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
    @Transactional
    public int insertCustomer(Customer customer) {
        // check unique customer name
        checkUniqueCustomer(customer);

        customer.setCreateTime(DateUtils.getNowDate());
        int insert = customerMapper.insertCustomer(customer);
        Long customerId = customer.getId();

        // add a default customer dept
        String customerDeptNo = customerDeptService.generateCustomerDeptNo(customerId, customer.getShowMnemonicCode(), true);
        CustomerDept customerDept = CustomerDept.builder()
                .customerId(customerId)
                .parentId(0L)
                .code(customerDeptNo)
                .name(customer.getShowName())
                .mnemonicCode(customer.getShowMnemonicCode())
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
    @Transactional
    public int updateCustomer(Customer customer) {
        // check unique customer name
        checkUniqueCustomer(customer);

        customer.setUpdateTime(DateUtils.getNowDate());
        int updated = customerMapper.updateCustomer(customer);

        // update customer dept
        CustomerDept cd = new CustomerDept();
        cd.setCustomerId(customer.getId());
        cd.setParentId(0L);
        CustomerDept customerDept = customerDeptMapper.selectCustomerDeptList(cd).stream()
                .findFirst()
                .orElseThrow(() -> new ServiceException("customer dept not found"));
        customerDept.setName(customer.getShowName());
        customerDept.setMnemonicCode(customer.getShowMnemonicCode());
        customerDeptService.updateCustomerDept(customerDept);

        return updated;
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

package com.lin.distribution.service.impl;

import java.util.List;

import com.lin.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.service.CustomerDeptService;

/**
 * 客户部门Service业务层处理
 *
 * @author lin
 * @date 2024-11-09
 */
@Service
public class CustomerDeptServiceImpl implements CustomerDeptService {
    @Autowired
    private CustomerDeptMapper customerDeptMapper;

    /**
     * 查询客户部门
     *
     * @param id 客户部门主键
     * @return 客户部门
     */
    @Override
    public CustomerDept selectCustomerDeptById(Long id) {
        return customerDeptMapper.selectCustomerDeptById(id);
    }

    /**
     * 查询客户部门列表
     *
     * @param customerDept 客户部门
     * @return 客户部门
     */
    @Override
    public List<CustomerDept> selectCustomerDeptList(CustomerDept customerDept) {
        return customerDeptMapper.selectCustomerDeptList(customerDept);
    }

    /**
     * 新增客户部门
     *
     * @param customerDept 客户部门
     * @return 结果
     */
    @Override
    public int insertCustomerDept(CustomerDept customerDept) {
        customerDept.setCreateTime(DateUtils.getNowDate());
        return customerDeptMapper.insertCustomerDept(customerDept);
    }

    /**
     * 修改客户部门
     *
     * @param customerDept 客户部门
     * @return 结果
     */
    @Override
    public int updateCustomerDept(CustomerDept customerDept) {
        customerDept.setUpdateTime(DateUtils.getNowDate());
        return customerDeptMapper.updateCustomerDept(customerDept);
    }

    /**
     * 批量删除客户部门
     *
     * @param ids 需要删除的客户部门主键
     * @return 结果
     */
    @Override
    public int deleteCustomerDeptByIds(Long[] ids) {
        return customerDeptMapper.deleteCustomerDeptByIds(ids);
    }

    /**
     * 删除客户部门信息
     *
     * @param id 客户部门主键
     * @return 结果
     */
    @Override
    public int deleteCustomerDeptById(Long id) {
        return customerDeptMapper.deleteCustomerDeptById(id);
    }
}

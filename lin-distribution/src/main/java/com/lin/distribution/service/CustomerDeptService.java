package com.lin.distribution.service;

import com.lin.distribution.domain.CustomerDept;

import java.util.List;

/**
 * 客户部门Service接口
 *
 * @author lin
 * @date 2024-11-09
 */
public interface CustomerDeptService
{
    /**
     * 查询客户部门
     *
     * @param id 客户部门主键
     * @return 客户部门
     */
    CustomerDept selectCustomerDeptById(Long id);

    /**
     * 查询父级部门(parentId=0)
     * @param customerId
     * @return
     */
    CustomerDept selectOneParentCustomerDept(Long customerId);

    /**
     * 查询客户部门列表
     *
     * @param customerDept 客户部门
     * @return 客户部门集合
     */
    List<CustomerDept> selectCustomerDeptList(CustomerDept customerDept);

    /**
     * 新增客户部门
     *
     * @param customerDept 客户部门
     * @return 结果
     */
    int insertCustomerDept(CustomerDept customerDept);

    /**
     * 修改客户部门
     *
     * @param customerDept 客户部门
     * @return 结果
     */
    int updateCustomerDept(CustomerDept customerDept);

    /**
     * 批量删除客户部门
     *
     * @param ids 需要删除的客户部门主键集合
     * @return 结果
     */
    int deleteCustomerDeptByIds(Long[] ids);

    /**
     * 删除客户部门信息
     *
     * @param id 客户部门主键
     * @return 结果
     */
    int deleteCustomerDeptById(Long id);

    /**
     * 生成客户部门编号
     *
     * @param customerId
     * @param mnemonicCode
     * @param isParent
     * @return
     */
    String generateCustomerDeptNo(Long customerId, String mnemonicCode, Boolean isParent);
}
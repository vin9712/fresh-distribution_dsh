package com.lin.distribution.mapper;

import com.lin.distribution.domain.CustomerDept;

import java.util.List;

/**
 * 客户部门Mapper接口
 *
 * @author lin
 * @date 2024-11-09
 */
public interface CustomerDeptMapper {
    /**
     * 查询客户部门
     *
     * @param id 客户部门主键
     * @return 客户部门
     */
    CustomerDept selectCustomerDeptById(Long id);

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
     * 删除客户部门
     *
     * @param id 客户部门主键
     * @return 结果
     */
    int deleteCustomerDeptById(Long id);

    /**
     * 批量删除客户部门
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteCustomerDeptByIds(Long[] ids);

    /**
     * 校验客户部门是否重复
     *
     * @param customerDept 客户部门
     * @return 结果
     */
    List<CustomerDept> checkUniqueCustomerDept(CustomerDept customerDept);
}

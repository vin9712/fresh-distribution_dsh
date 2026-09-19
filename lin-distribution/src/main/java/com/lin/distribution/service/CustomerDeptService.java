package com.lin.distribution.service;

import com.lin.distribution.domain.CustomerDept;

import java.util.List;

/**
 * 配送点Service接口
 *
 * @author lin
 * @date 2024-11-09
 */
public interface CustomerDeptService
{
    /**
     * 查询客户部门
     *
     * @param id 配送点主键
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
     * @return 配送点集合
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
     * @param ids 需要删除的配送点主键集合
     * @return 结果
     */
    int deleteCustomerDeptByIds(Long[] ids);

    /**
     * 删除配送点信息
     *
     * @param id 配送点主键
     * @return 结果
     */
    int deleteCustomerDeptById(Long id);

    /**
     * 生成配送点编号
     *
     * @param customerId
     * @param mnemonicCode
     * @param isParent
     * @return
     */
    String generateCustomerDeptNo(Long customerId, String mnemonicCode, Boolean isParent);

    /**
     * 批量排序配送点（D-074 总单列顺序）：按 ids 先后顺序重排为 sortNo = 1..N
     *
     * @param customerId 客户ID（校验 ids 均属于该客户）
     * @param ids        配送点ID有序集合
     * @return 更新条数
     */
    int sortCustomerDepts(Long customerId, List<Long> ids);
}
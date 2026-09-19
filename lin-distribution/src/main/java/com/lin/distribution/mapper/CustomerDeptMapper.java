package com.lin.distribution.mapper;

import com.lin.distribution.domain.CustomerDept;

import java.util.List;

/**
 * 配送点Mapper接口
 *
 * @author lin
 * @date 2024-11-09
 */
public interface CustomerDeptMapper {
    /**
     * 查询客户部门
     *
     * @param id 配送点主键
     * @return 客户部门
     */
    CustomerDept selectCustomerDeptById(Long id);

    /**
     * 批量查客户部门（AC-6 验收明细配送点名回填用，防 N+1）
     *
     * @param ids 配送点ID集合
     * @return 客户部门集合
     */
    List<CustomerDept> selectCustomerDeptByIds(@org.apache.ibatis.annotations.Param("ids") List<Long> ids);

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
     * 删除客户部门
     *
     * @param id 配送点主键
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

    /**
     * 更新单个配送点排序（D-074 总单列顺序）
     */
    int updateSortNo(@org.apache.ibatis.annotations.Param("id") Long id,
                     @org.apache.ibatis.annotations.Param("sortNo") Integer sortNo);
}

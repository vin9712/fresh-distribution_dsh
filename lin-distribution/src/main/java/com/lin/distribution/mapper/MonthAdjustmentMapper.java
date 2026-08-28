package com.lin.distribution.mapper;

import com.lin.distribution.domain.MonthAdjustment;

import java.util.List;

/**
 * 下月调整单Mapper接口（W0-2.7）
 *
 * @author dsh
 */
public interface MonthAdjustmentMapper {

    /**
     * 根据ID查询调整单
     *
     * @param id 调整单ID
     * @return 调整单
     */
    MonthAdjustment selectById(Long id);

    /**
     * 查询调整单列表（is_deleted=0）
     *
     * @param query 查询条件（customerId/billMonth/status）
     * @return 调整单集合
     */
    List<MonthAdjustment> selectList(MonthAdjustment query);

    /**
     * 新增调整单
     *
     * @param adjustment 调整单
     * @return 结果
     */
    int insert(MonthAdjustment adjustment);

    /**
     * 修改调整单
     *
     * @param adjustment 调整单
     * @return 结果
     */
    int update(MonthAdjustment adjustment);

    /**
     * 物理删除（草稿逻辑删除，置 is_deleted=1）
     *
     * @param id 调整单ID
     * @return 结果
     */
    int deleteById(Long id);
}

package com.lin.distribution.service;

import com.lin.distribution.domain.MonthAdjustment;

import java.util.List;

/**
 * 下月调整单Service接口（蓝图 W0-2.7）
 *
 * @author dsh
 */
public interface MonthAdjustmentService {

    /**
     * 查询调整单列表
     *
     * @param query 查询条件
     * @return 调整单集合
     */
    List<MonthAdjustment> selectList(MonthAdjustment query);

    /**
     * 根据ID查询调整单
     *
     * @param id 调整单ID
     * @return 调整单
     */
    MonthAdjustment selectById(Long id);

    /**
     * 新增下月调整单（草稿）：独立单号、客户/结算月校验、金额非负、应收与成本分项独立留痕
     *
     * @param adjustment 调整单（含 customerId/billMonth/receivableAmount/purchaseCostAmount/remark）
     * @return 生成的调整单
     */
    MonthAdjustment create(MonthAdjustment adjustment);

    /**
     * 修改草稿调整单
     *
     * @param adjustment 调整单（含 id 与变更字段）
     * @return 结果
     */
    int update(MonthAdjustment adjustment);

    /**
     * 提交调整单（草稿→已提交）：提交后立即参与客户对账与经营概览重算（W0-3）
     *
     * @param id 调整单ID
     * @return 结果
     */
    int submit(Long id);

    /**
     * 删除调整单（仅草稿，逻辑删除）
     *
     * @param id 调整单ID
     * @return 结果
     */
    int delete(Long id);
}

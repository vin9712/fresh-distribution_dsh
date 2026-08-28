package com.lin.distribution.mapper;

import com.lin.distribution.domain.MonthSettlement;

import java.util.List;

/**
 * 客户月度结算Mapper接口（W0-3.1）
 *
 * @author dsh
 */
public interface MonthSettlementMapper {

    /**
     * 根据客户+结算月查询结算记录
     *
     * @param customerId 客户ID
     * @param billMonth  结算月份（yyyy-MM）
     * @return 结算记录（不存在返回 null）
     */
    MonthSettlement selectByCustomerAndMonth(Long customerId, String billMonth);

    /**
     * 查询结算记录列表
     *
     * @param query 查询条件（customerId/billMonth/status）
     * @return 结算记录集合
     */
    List<MonthSettlement> selectList(MonthSettlement query);

    /**
     * 新增结算记录
     *
     * @param settlement 结算记录
     * @return 结果
     */
    int insert(MonthSettlement settlement);

    /**
     * 修改结算记录
     *
     * @param settlement 结算记录
     * @return 结果
     */
    int update(MonthSettlement settlement);

    /**
     * 判断某月是否存在已月结记录（采购单无 customer_id，按月粒度冻结用）
     *
     * @param billMonth 结算月份（yyyy-MM）
     * @return 存在=1
     */
    int existsByMonth(String billMonth);
}

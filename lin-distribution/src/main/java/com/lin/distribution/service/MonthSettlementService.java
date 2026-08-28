package com.lin.distribution.service;

import com.lin.distribution.domain.MonthSettlement;
import com.lin.distribution.vo.MonthSettlementPreviewVO;

import java.util.List;

/**
 * 客户月度结算Service接口（蓝图 W0-3.1 按客户月结）
 *
 * @author dsh
 */
public interface MonthSettlementService {

    /**
     * 预览某客户某月的验收单与下月调整单及结算汇总（月结前核对用）
     *
     * @param customerId 客户ID
     * @param billMonth  结算月份（yyyy-MM）
     * @return 结算预览
     */
    MonthSettlementPreviewVO preview(Long customerId, String billMonth);

    /**
     * 执行客户月结：按「客户 + 结算月」落已结记录；月结后该客户该月验收/采购成本/调整单/退货单冻结
     *
     * @param customerId 客户ID
     * @param billMonth  结算月份（yyyy-MM）
     * @param remark     结算备注
     * @return 结算记录
     */
    MonthSettlement settle(Long customerId, String billMonth, String remark);

    /**
     * 判断某客户某月是否已月结（冻结校验用）
     *
     * @param customerId 客户ID
     * @param billMonth  结算月份（yyyy-MM）
     * @return true=已月结（冻结）
     */
    boolean isSettled(Long customerId, String billMonth);

    /**
     * 查询结算记录列表
     *
     * @param query 查询条件
     * @return 结算记录集合
     */
    List<MonthSettlement> selectList(MonthSettlement query);

    /**
     * 根据客户+月份查询结算记录
     *
     * @param customerId 客户ID
     * @param billMonth  结算月份（yyyy-MM）
     * @return 结算记录（不存在返回 null）
     */
    MonthSettlement getByCustomerAndMonth(Long customerId, String billMonth);
}

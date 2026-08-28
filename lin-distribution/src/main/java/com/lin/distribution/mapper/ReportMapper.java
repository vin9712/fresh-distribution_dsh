package com.lin.distribution.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.lin.distribution.domain.ReportRow;
import org.apache.ibatis.annotations.Param;

/**
 * 报表查询Mapper接口（销售日报/客户对账单，DESIGN.md §10）
 *
 * @author dsh
 */
public interface ReportMapper {
    /**
     * 销售日报：按配送日期查已提交验收单明细（含客户/配送点/送货单关联）
     *
     * @param deliveryDate 配送日期
     * @return 明细扁平行
     */
    List<ReportRow> selectDailySaleRows(@Param("deliveryDate") LocalDate deliveryDate);

    /**
     * 客户对账单：按客户+验收日期区间查已提交验收单明细
     *
     * @param customerId 客户ID
     * @param beginDate  起始日期（含）
     * @param endDate    结束日期（含）
     * @return 明细扁平行
     */
    List<ReportRow> selectStatementRows(@Param("customerId") Long customerId,
                                        @Param("beginDate") LocalDate beginDate,
                                        @Param("endDate") LocalDate endDate);

    /**
     * 经营概览：按验收日期区间聚合已提交验收单的应收金额，并按「客户该月是否已月结」分桶
     *
     * @param beginDate 起始日期（含）
     * @param endDate   结束日期（含）
     * @return 已/未月结分桶行
     */
    List<com.lin.distribution.dto.ReportVO.OverviewSettleAmount> selectOverviewAccepted(
            @Param("beginDate") LocalDate beginDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 经营概览：按采购单归属日期区间聚合采购总额（不含已作废）
     *
     * @param beginDate 起始日期（含）
     * @param endDate   结束日期（含）
     * @return 采购总额
     */
    BigDecimal selectOverviewPurchase(@Param("beginDate") LocalDate beginDate,
                                      @Param("endDate") LocalDate endDate);

    /**
     * 经营概览：按采购单归属日期区间聚合「待确认成本」（草稿+已确认，未入库）
     *
     * @param beginDate 起始日期（含）
     * @param endDate   结束日期（含）
     * @return 待确认成本总额
     */
    BigDecimal selectOverviewPendingCost(@Param("beginDate") LocalDate beginDate,
                                         @Param("endDate") LocalDate endDate);
}

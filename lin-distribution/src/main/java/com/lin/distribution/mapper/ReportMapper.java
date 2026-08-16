package com.lin.distribution.mapper;

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
}

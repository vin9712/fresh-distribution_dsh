package com.lin.distribution.service;

import java.time.LocalDate;
import java.util.List;

import com.lin.distribution.dto.ReportVO;

/**
 * 报表Service接口（DESIGN.md §10）
 *
 * @author dsh
 */
public interface ReportService {
    /**
     * 销售日报：按配送日期、按客户+配送点分组
     *
     * @param deliveryDate 配送日期
     * @return 分组列表
     */
    List<ReportVO.DailySaleGroup> dailySale(LocalDate deliveryDate);

    /**
     * 客户对账单：按客户+验收日期区间（默认自然月）
     *
     * @param customerId 客户ID
     * @param beginDate  起始日期（含）
     * @param endDate    结束日期（含）
     * @return 对账单
     */
    ReportVO.CustomerStatement customerStatement(Long customerId, LocalDate beginDate, LocalDate endDate);
}

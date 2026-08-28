package com.lin.distribution.mapper;

import com.lin.distribution.domain.WorkbenchSummary;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作台待办统计 Mapper
 *
 * @author dsh
 */
public interface WorkbenchMapper {

    WorkbenchSummary selectSummary(@Param("tomorrow") LocalDate tomorrow);

    /**
     * 工作台待验收提醒（W0-3.2）：已送达未提交验收的送货单，附最近打印时间/配送日期
     *
     * @return 待验收送货单明细
     */
    List<com.lin.distribution.vo.PendingAcceptanceVO> selectPendingAcceptance();
}

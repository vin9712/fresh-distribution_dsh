package com.lin.distribution.mapper;

import com.lin.distribution.domain.WorkbenchSummary;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 工作台待办统计 Mapper
 *
 * @author dsh
 */
public interface WorkbenchMapper {

    WorkbenchSummary selectSummary(@Param("tomorrow") LocalDate tomorrow);
}

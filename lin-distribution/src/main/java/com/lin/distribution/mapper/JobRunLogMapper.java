package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.JobRunLog;

/**
 * 定时任务运行记录Mapper接口（S14/Q36/D-037 工作台告警数据源）
 *
 * @author dsh
 */
public interface JobRunLogMapper {

    /**
     * 查询任务运行记录
     *
     * @param id 主键
     * @return 任务运行记录
     */
    JobRunLog selectJobRunLogById(Long id);

    /**
     * 查询任务运行记录列表
     *
     * @param jobRunLog 查询条件
     * @return 任务运行记录集合
     */
    List<JobRunLog> selectJobRunLogList(JobRunLog jobRunLog);

    /**
     * 新增任务运行记录
     *
     * @param jobRunLog 任务运行记录
     * @return 影响行数
     */
    int insertJobRunLog(JobRunLog jobRunLog);

    /**
     * 查询指定任务最近一次运行记录（工作台生成异常告警数据源，Q36/D-037）
     *
     * @param jobName 任务名（DELIVERY_GENERATE）
     * @return 最近一次运行记录（无则 null）
     */
    JobRunLog selectLatestByJobName(String jobName);
}

package com.lin.distribution.mapper;

import java.util.List;
import com.lin.distribution.domain.PrintTask;

/**
 * 打印任务Mapper接口
 *
 * @author lin
 * @date 2025-01-07
 */
public interface PrintTaskMapper
{
    /**
     * 查询打印任务
     *
     * @param id 打印任务主键
     * @return 打印任务
     */
    PrintTask selectPrintTaskById(Long id);

    /**
     * 查询打印任务列表
     *
     * @param printTask 打印任务
     * @return 打印任务集合
     */
    List<PrintTask> selectPrintTaskList(PrintTask printTask);

    /**
     * 获取上一次打印任务
     * @param orderId
     * @return
     */
    PrintTask selectLatestOneByOrderId(Long orderId);

    /**
     * 新增打印任务
     *
     * @param printTask 打印任务
     * @return 结果
     */
    int insertPrintTask(PrintTask printTask);

    /**
     * 修改打印任务
     *
     * @param printTask 打印任务
     * @return 结果
     */
    int updatePrintTask(PrintTask printTask);

    /**
     * 删除打印任务
     *
     * @param id 打印任务主键
     * @return 结果
     */
    int deletePrintTaskById(Long id);

    /**
     * 批量删除打印任务
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deletePrintTaskByIds(Long[] ids);
}
package com.lin.distribution.service.impl;

import java.util.List;

import com.lin.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.PrintTaskMapper;
import com.lin.distribution.domain.PrintTask;
import com.lin.distribution.service.PrintTaskService;

/**
 * 打印任务Service业务层处理
 *
 * @author lin
 * @date 2025-01-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintTaskServiceImpl implements PrintTaskService {
    private final PrintTaskMapper printTaskMapper;

    /**
     * 查询打印任务
     *
     * @param id 打印任务主键
     * @return 打印任务
     */
    @Override
    public PrintTask selectPrintTaskById(Long id) {
        return printTaskMapper.selectPrintTaskById(id);
    }

    /**
     * 查询打印任务列表
     *
     * @param printTask 打印任务
     * @return 打印任务
     */
    @Override
    public List<PrintTask> selectPrintTaskList(PrintTask printTask) {
        return printTaskMapper.selectPrintTaskList(printTask);
    }

    /**
     * 新增打印任务
     *
     * @param printTask 打印任务
     * @return 结果
     */
    @Override
    public int insertPrintTask(PrintTask printTask) {
        printTask.setCreateTime(DateUtils.getNowDate());
        return printTaskMapper.insertPrintTask(printTask);
    }

    /**
     * 修改打印任务
     *
     * @param printTask 打印任务
     * @return 结果
     */
    @Override
    public int updatePrintTask(PrintTask printTask) {
        printTask.setUpdateTime(DateUtils.getNowDate());
        return printTaskMapper.updatePrintTask(printTask);
    }

    /**
     * 批量删除打印任务
     *
     * @param ids 需要删除的打印任务主键
     * @return 结果
     */
    @Override
    public int deletePrintTaskByIds(Long[] ids) {
        return printTaskMapper.deletePrintTaskByIds(ids);
    }

    /**
     * 删除打印任务信息
     *
     * @param id 打印任务主键
     * @return 结果
     */
    @Override
    public int deletePrintTaskById(Long id) {
        return printTaskMapper.deletePrintTaskById(id);
    }
}
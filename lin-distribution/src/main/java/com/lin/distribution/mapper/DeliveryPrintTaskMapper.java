package com.lin.distribution.mapper;

import java.util.List;
import java.util.Map;

import com.lin.distribution.domain.DeliveryPrintTask;

import org.apache.ibatis.annotations.Param;

/**
 * 送货单打印任务 Mapper（P2/D-050：包内一张单）
 *
 * @author dsh
 */
public interface DeliveryPrintTaskMapper {

    /**
     * 批量插入包内任务
     *
     * @param tasks 任务集合
     * @return 结果
     */
    int batchInsertPrintTask(List<DeliveryPrintTask> tasks);

    /**
     * 查包内任务清单（含送货单/模板展示字段，按 seq_no 升序）
     *
     * @param packageId 打印包ID
     * @return 任务集合
     */
    List<DeliveryPrintTask> selectTasksByPackageId(Long packageId);

    /**
     * 查单个任务（含展示字段）
     *
     * @param id 任务ID
     * @return 任务（无则 null）
     */
    DeliveryPrintTask selectTaskById(Long id);

    /**
     * 单任务更新（状态/失败原因/回执时间/尝试次数/模板/份数）
     *
     * @param task 任务
     * @return 结果
     */
    int updatePrintTask(DeliveryPrintTask task);

    /**
     * 包内任务计数（按状态分桶）
     *
     * @param packageId 打印包ID
     * @return [{status, cnt}]
     */
    List<Map<String, Object>> countByStatus(Long packageId);
}

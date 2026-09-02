package com.lin.distribution.mapper;

import java.time.LocalDate;
import java.util.List;

import com.lin.distribution.domain.DeliveryPrintLog;

/**
 * 送货单打印日志 Mapper（D-055：打印分界）
 *
 * @author dsh
 */
public interface DeliveryPrintLogMapper {

    /** 插入打印日志 */
    int insertPrintLog(DeliveryPrintLog log);

    /**
     * 该 客户+日期+点 是否已打印（存在记录=已打印=配送后）
     *
     * @param customerId     客户ID
     * @param deliveryDate   配送日期
     * @param customerDeptId 配送点ID（总单传 NULL）
     * @return 已打印记录数
     */
    int countPrinted(Long customerId, LocalDate deliveryDate, Long customerDeptId);

    /** 查最近打印记录（展示用，按时间倒序） */
    List<DeliveryPrintLog> selectPrintLogs(DeliveryPrintLog query);
}

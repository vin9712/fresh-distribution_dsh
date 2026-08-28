package com.lin.distribution.mapper;

import com.lin.distribution.domain.PurchaseModifyLog;

import java.util.List;

/**
 * 已确认采购单调整审计日志Mapper接口（W0-2.5）
 *
 * @author dsh
 */
public interface PurchaseModifyLogMapper {

    /**
     * 查询某采购单的调整日志列表（按操作时间倒序）
     *
     * @param purchaseId 采购单ID
     * @return 调整日志集合
     */
    List<PurchaseModifyLog> selectListByPurchaseId(Long purchaseId);

    /**
     * 新增调整日志
     *
     * @param log 调整日志
     * @return 结果
     */
    int insertPurchaseModifyLog(PurchaseModifyLog log);
}

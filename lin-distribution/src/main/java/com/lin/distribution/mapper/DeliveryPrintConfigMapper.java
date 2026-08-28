package com.lin.distribution.mapper;

import com.lin.distribution.domain.DeliveryPrintConfig;

/**
 * 送货单打印拆分配置Mapper接口（W0-2.2）
 *
 * @author dsh
 */
public interface DeliveryPrintConfigMapper {

    /**
     * 按主键查询打印配置
     *
     * @param id 主键
     * @return 打印配置
     */
    DeliveryPrintConfig selectDeliveryPrintConfigById(Long id);

    /**
     * 按送货单ID查询当前打印配置（一张送货单一份）
     *
     * @param deliveryOrderId 送货单ID
     * @return 打印配置（无则为 null）
     */
    DeliveryPrintConfig selectByDeliveryOrderId(Long deliveryOrderId);

    /**
     * 按送货单ID查询当前打印配置并锁行（UPSERT 防并发重复建配置）
     *
     * @param deliveryOrderId 送货单ID
     * @return 打印配置（无则为 null）
     */
    DeliveryPrintConfig selectByDeliveryOrderIdForUpdate(Long deliveryOrderId);

    /**
     * 新增打印配置
     *
     * @param config 打印配置
     * @return 影响行数
     */
    int insertDeliveryPrintConfig(DeliveryPrintConfig config);

    /**
     * 修改打印配置
     *
     * @param config 打印配置
     * @return 影响行数
     */
    int updateDeliveryPrintConfig(DeliveryPrintConfig config);
}

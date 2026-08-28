package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.DeliveryPrintConfigVersion;

/**
 * 送货单打印配置版本记录Mapper接口（W0-2.2）
 *
 * @author dsh
 */
public interface DeliveryPrintConfigVersionMapper {

    /**
     * 按送货单ID查询全部版本（倒序：最新的在前）
     *
     * @param deliveryOrderId 送货单ID
     * @return 版本记录集合
     */
    List<DeliveryPrintConfigVersion> selectByDeliveryOrderId(Long deliveryOrderId);

    /**
     * 查询某送货单当前最大版本序号（无记录则 0）
     *
     * @param deliveryOrderId 送货单ID
     * @return 最大版本序号（无则 0）
     */
    Integer selectMaxVersionNo(Long deliveryOrderId);

    /**
     * 新增版本记录
     *
     * @param version 版本记录
     * @return 影响行数
     */
    int insertDeliveryPrintConfigVersion(DeliveryPrintConfigVersion version);
}

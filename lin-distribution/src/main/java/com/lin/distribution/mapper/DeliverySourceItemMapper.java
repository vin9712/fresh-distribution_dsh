package com.lin.distribution.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.lin.distribution.domain.DeliverySourceItem;

/**
 * 送货来源明细Mapper接口（S14：订单行→送货行分配台账）
 *
 * @author dsh
 */
public interface DeliverySourceItemMapper {

    /**
     * 查询送货来源明细
     *
     * @param id 主键
     * @return 送货来源明细
     */
    DeliverySourceItem selectDeliverySourceItemById(Long id);

    /**
     * 查询送货来源明细列表
     *
     * @param deliverySourceItem 查询条件
     * @return 送货来源明细集合
     */
    List<DeliverySourceItem> selectDeliverySourceItemList(DeliverySourceItem deliverySourceItem);

    /**
     * 按送货单查有效来源分配（来源对照/状态回写/撤回判断用）
     *
     * @param deliveryId 送货单ID
     * @return 送货来源明细集合
     */
    List<DeliverySourceItem> selectListByDeliveryId(Long deliveryId);

    /**
     * 批量新增送货来源明细
     *
     * @param items 送货来源明细集合
     * @return 影响行数
     */
    int batchInsertDeliverySourceItem(List<DeliverySourceItem> items);

    /**
     * 修改送货来源明细
     *
     * @param deliverySourceItem 送货来源明细
     * @return 影响行数
     */
    int updateDeliverySourceItem(DeliverySourceItem deliverySourceItem);

    /**
     * 作废释放：按送货单逻辑删除来源分配
     *
     * @param deliveryId 送货单ID
     * @return 影响行数
     */
    int deleteByDeliveryId(Long deliveryId);

    /**
     * 撤回级联（W0-2.1）：查订单在有效（非作废）送货单上的来源分配
     *
     * @param saleOrderId 销售订单ID
     * @return 送货来源明细集合
     */
    List<DeliverySourceItem> selectValidBySaleOrderId(Long saleOrderId);

    /**
     * 撤回级联（W0-2.1）：按送货单+来源订单软删释放来源分配（旧行保留审计）
     *
     * @param deliveryId  送货单ID
     * @param saleOrderId 来源销售订单ID
     * @return 影响行数
     */
    int deleteByDeliveryIdAndSaleOrderId(@Param("deliveryId") Long deliveryId, @Param("saleOrderId") Long saleOrderId);
}

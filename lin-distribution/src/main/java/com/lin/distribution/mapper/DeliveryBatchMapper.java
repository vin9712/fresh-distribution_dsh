package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.DeliveryBatch;
import org.apache.ibatis.annotations.Param;

/**
 * 客户每日配送批次Mapper接口（S14 第一层）
 *
 * @author dsh
 */
public interface DeliveryBatchMapper {

    /**
     * 查询配送批次
     *
     * @param id 批次主键
     * @return 配送批次
     */
    DeliveryBatch selectDeliveryBatchById(Long id);

    /**
     * 查询配送批次列表
     *
     * @param deliveryBatch 查询条件
     * @return 配送批次集合
     */
    List<DeliveryBatch> selectDeliveryBatchList(DeliveryBatch deliveryBatch);

    /**
     * 按客户+配送日期查有效批次（生成服务幂等判断用）
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 配送批次（无则 null）
     */
    DeliveryBatch selectByCustomerAndDate(@Param("customerId") Long customerId, @Param("deliveryDate") String deliveryDate);

    /**
     * 新增配送批次
     *
     * @param deliveryBatch 配送批次
     * @return 影响行数
     */
    int insertDeliveryBatch(DeliveryBatch deliveryBatch);

    /**
     * 修改配送批次
     *
     * @param deliveryBatch 配送批次
     * @return 影响行数
     */
    int updateDeliveryBatch(DeliveryBatch deliveryBatch);
}

package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.DeliverySkuOverride;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 配送点商品覆盖Mapper接口
 *
 * @author dsh
 */
public interface DeliverySkuOverrideMapper {

    DeliverySkuOverride selectDeliverySkuOverrideById(Long id);

    List<DeliverySkuOverride> selectDeliverySkuOverrideList(DeliverySkuOverride deliverySkuOverride);

    /**
     * 取价：配送点+SKU 在生效期内最新的一条覆盖
     */
    DeliverySkuOverride selectActiveByPointAndSku(@Param("deliveryPointId") Long deliveryPointId,
                                                  @Param("skuId") Long skuId,
                                                  @Param("deliveryDate") LocalDate deliveryDate);

    /**
     * 按配送点批量查询覆盖（客户商品列表合并用）
     */
    List<DeliverySkuOverride> selectByPoint(@Param("deliveryPointId") Long deliveryPointId);

    int insertDeliverySkuOverride(DeliverySkuOverride deliverySkuOverride);

    int updateDeliverySkuOverride(DeliverySkuOverride deliverySkuOverride);

    int deleteDeliverySkuOverrideById(Long id);

    int deleteDeliverySkuOverrideByIds(Long[] ids);
}

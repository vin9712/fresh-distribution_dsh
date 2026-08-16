package com.lin.distribution.mapper;

import com.lin.distribution.domain.DeliveryPointPrice;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 配送点报价 Mapper
 *
 * @author dsh
 */
public interface DeliveryPointPriceMapper {

    DeliveryPointPrice selectDeliveryPointPriceById(Long id);

    List<DeliveryPointPrice> selectDeliveryPointPriceList(DeliveryPointPrice deliveryPointPrice);

    int insertDeliveryPointPrice(DeliveryPointPrice deliveryPointPrice);

    int updateDeliveryPointPrice(DeliveryPointPrice deliveryPointPrice);

    int deleteDeliveryPointPriceByIds(Long[] ids);

    /** 取价：配送点在有效期区间内的最新报价 */
    DeliveryPointPrice selectActivePriceByPointAndSku(@Param("deliveryPointId") Long deliveryPointId,
                                                      @Param("skuId") Long skuId,
                                                      @Param("priceDate") LocalDate priceDate);
}

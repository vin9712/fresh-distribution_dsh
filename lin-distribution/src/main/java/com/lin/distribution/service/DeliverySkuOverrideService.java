package com.lin.distribution.service;

import com.lin.distribution.domain.DeliverySkuOverride;

import java.util.List;

/**
 * 配送点商品覆盖Service接口（delivery_sku_override，替代原 delivery_point_price）
 *
 * @author dsh
 */
public interface DeliverySkuOverrideService {

    DeliverySkuOverride selectDeliverySkuOverrideById(Long id);

    List<DeliverySkuOverride> selectDeliverySkuOverrideList(DeliverySkuOverride deliverySkuOverride);

    /**
     * 新增/更新配送点覆盖（同配送点+SKU 唯一，存在则覆盖更新）
     */
    int saveDeliverySkuOverride(DeliverySkuOverride deliverySkuOverride);

    int deleteDeliverySkuOverrideByIds(Long[] ids);
}

package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.DeliverySkuOverride;
import com.lin.distribution.mapper.DeliverySkuOverrideMapper;
import com.lin.distribution.service.DeliverySkuOverrideService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 配送点商品覆盖服务实现（deepseek_redesign.md §3.8 / §5.2）
 *
 * @author dsh
 */
@Service
@RequiredArgsConstructor
public class DeliverySkuOverrideServiceImpl implements DeliverySkuOverrideService {

    private final DeliverySkuOverrideMapper deliverySkuOverrideMapper;

    @Override
    public DeliverySkuOverride selectDeliverySkuOverrideById(Long id) {
        return deliverySkuOverrideMapper.selectDeliverySkuOverrideById(id);
    }

    @Override
    public List<DeliverySkuOverride> selectDeliverySkuOverrideList(DeliverySkuOverride deliverySkuOverride) {
        return deliverySkuOverrideMapper.selectDeliverySkuOverrideList(deliverySkuOverride);
    }

    @Override
    public int saveDeliverySkuOverride(DeliverySkuOverride deliverySkuOverride) {
        if (deliverySkuOverride == null) {
            throw new ServiceException("delivery sku override is null");
        }
        if (deliverySkuOverride.getDeliveryPointId() == null) {
            throw new ServiceException("配送点不能为空");
        }
        if (deliverySkuOverride.getSkuId() == null) {
            throw new ServiceException("SKU不能为空");
        }
        if (deliverySkuOverride.getIsAvailable() == null) {
            deliverySkuOverride.setIsAvailable(1);
        }
        // 同配送点+SKU 唯一（uk_delivery_sku），存在则更新，不存在则插入
        DeliverySkuOverride exist = findExisting(deliverySkuOverride.getDeliveryPointId(), deliverySkuOverride.getSkuId());
        if (exist != null) {
            deliverySkuOverride.setId(exist.getId());
            return deliverySkuOverrideMapper.updateDeliverySkuOverride(deliverySkuOverride);
        }
        return deliverySkuOverrideMapper.insertDeliverySkuOverride(deliverySkuOverride);
    }

    private DeliverySkuOverride findExisting(Long deliveryPointId, Long skuId) {
        DeliverySkuOverride query = new DeliverySkuOverride();
        query.setDeliveryPointId(deliveryPointId);
        query.setSkuId(skuId);
        List<DeliverySkuOverride> list = deliverySkuOverrideMapper.selectDeliverySkuOverrideList(query);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int deleteDeliverySkuOverrideByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return deliverySkuOverrideMapper.deleteDeliverySkuOverrideByIds(ids);
    }
}

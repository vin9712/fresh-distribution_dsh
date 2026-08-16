package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.domain.DeliveryPointPrice;
import com.lin.distribution.mapper.DeliveryPointPriceMapper;
import com.lin.distribution.service.DeliveryPointPriceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 配送点报价Service业务层处理
 *
 * @author dsh
 */
@Service
public class DeliveryPointPriceServiceImpl implements DeliveryPointPriceService {

    @Autowired
    private DeliveryPointPriceMapper deliveryPointPriceMapper;

    @Override
    public DeliveryPointPrice selectDeliveryPointPriceById(Long id) {
        return deliveryPointPriceMapper.selectDeliveryPointPriceById(id);
    }

    @Override
    public List<DeliveryPointPrice> selectDeliveryPointPriceList(DeliveryPointPrice deliveryPointPrice) {
        return deliveryPointPriceMapper.selectDeliveryPointPriceList(deliveryPointPrice);
    }

    @Override
    public int insertDeliveryPointPrice(DeliveryPointPrice deliveryPointPrice) {
        validate(deliveryPointPrice);
        deliveryPointPrice.setCreateTime(DateUtils.getNowDate());
        return deliveryPointPriceMapper.insertDeliveryPointPrice(deliveryPointPrice);
    }

    @Override
    public int updateDeliveryPointPrice(DeliveryPointPrice deliveryPointPrice) {
        if (deliveryPointPrice == null || deliveryPointPrice.getId() == null) {
            throw new ServiceException("报价id不能为空");
        }
        deliveryPointPrice.setUpdateTime(DateUtils.getNowDate());
        return deliveryPointPriceMapper.updateDeliveryPointPrice(deliveryPointPrice);
    }

    @Override
    public int deleteDeliveryPointPriceByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return deliveryPointPriceMapper.deleteDeliveryPointPriceByIds(ids);
    }

    private void validate(DeliveryPointPrice deliveryPointPrice) {
        if (deliveryPointPrice == null) {
            throw new ServiceException("delivery point price is null");
        }
        if (deliveryPointPrice.getDeliveryPointId() == null) {
            throw new ServiceException("配送点不能为空");
        }
        if (deliveryPointPrice.getSkuId() == null) {
            throw new ServiceException("SKU不能为空");
        }
        if (deliveryPointPrice.getUnitPrice() == null || deliveryPointPrice.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("单价必须大于0");
        }
    }
}

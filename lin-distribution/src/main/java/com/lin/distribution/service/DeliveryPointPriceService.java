package com.lin.distribution.service;

import com.lin.distribution.domain.DeliveryPointPrice;

import java.util.List;

/**
 * 配送点报价Service接口
 *
 * @author dsh
 */
public interface DeliveryPointPriceService {

    /** 查询配送点报价 */
    DeliveryPointPrice selectDeliveryPointPriceById(Long id);

    /** 查询配送点报价列表 */
    List<DeliveryPointPrice> selectDeliveryPointPriceList(DeliveryPointPrice deliveryPointPrice);

    /** 新增配送点报价 */
    int insertDeliveryPointPrice(DeliveryPointPrice deliveryPointPrice);

    /** 修改配送点报价 */
    int updateDeliveryPointPrice(DeliveryPointPrice deliveryPointPrice);

    /** 批量删除配送点报价 */
    int deleteDeliveryPointPriceByIds(Long[] ids);
}

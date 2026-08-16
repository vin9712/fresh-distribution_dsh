package com.lin.distribution.service;

import com.lin.distribution.domain.PriceQueryResult;

import java.time.LocalDate;

/**
 * 取价服务：配送点报价 > 客户报价 > 客户关联报价模板，未命中返回空价
 *
 * @author dsh
 */
public interface PriceQueryService {

    PriceQueryResult queryPrice(Long customerId, Long deliveryPointId, Long skuId, LocalDate deliveryDate);
}

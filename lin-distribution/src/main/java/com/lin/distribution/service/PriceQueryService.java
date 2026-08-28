package com.lin.distribution.service;

import com.lin.distribution.domain.PriceQueryResult;

import java.time.LocalDate;

/**
 * 取价服务：仅使用客户正式报价；未命中返回空价（由文员手工定价）
 *
 * @author dsh
 */
public interface PriceQueryService {

    PriceQueryResult queryPrice(Long customerId, Long deliveryPointId, Long skuId, LocalDate deliveryDate);
}

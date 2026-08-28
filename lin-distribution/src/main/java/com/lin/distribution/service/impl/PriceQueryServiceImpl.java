package com.lin.distribution.service.impl;

import com.lin.distribution.domain.PriceQueryResult;
import com.lin.distribution.domain.ProductSkuQuoteDetail;
import com.lin.distribution.mapper.ProductSkuQuoteDetailMapper;
import com.lin.distribution.service.PriceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 取价服务实现（客户端化ERP优化蓝图 §2「价格层级」，W0-1 价格口径简化）
 * 仅使用客户正式报价（已发布 + 有效期区间内最新）；未命中返回空价（不得自动转免费/零价）。
 * 原「配送点覆盖价」已随 delivery_sku_override 废弃（sql/s11）；
 * 原「客户关联报价模板」已随 price_template 体系下线（sql/w01_price_simplify.sql）。
 * 未命中报价时由文员手工定价（原因必填、留审计），服务端不再有任何回退链。
 *
 * @author dsh
 */
@Service
@RequiredArgsConstructor
public class PriceQueryServiceImpl implements PriceQueryService {

    private final ProductSkuQuoteDetailMapper productSkuQuoteDetailMapper;

    @Override
    public PriceQueryResult queryPrice(Long customerId, Long deliveryPointId, Long skuId, LocalDate deliveryDate) {
        // 客户报价：deliveryPointId 已不参与取价（价格层级取消配送点覆盖价），保留参数仅为接口兼容
        if (customerId != null) {
            ProductSkuQuoteDetail customerPrice = productSkuQuoteDetailMapper.selectActivePriceByCustomerAndSku(customerId, skuId, deliveryDate);
            // 蓝图「零价报价」：单价为 0 视为无报价，返回空价走手工定价
            if (customerPrice != null && customerPrice.getPrice() != null
                    && customerPrice.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                return PriceQueryResult.builder()
                        .skuId(skuId)
                        .price(customerPrice.getPrice())
                        .source(PriceQueryResult.SOURCE_CUSTOMER_QUOTE)
                        .sourceId(customerPrice.getId())
                        .build();
            }
        }
        // 未命中：返回空价
        return PriceQueryResult.empty(skuId);
    }
}

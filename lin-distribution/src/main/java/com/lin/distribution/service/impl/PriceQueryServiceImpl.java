package com.lin.distribution.service.impl;

import com.lin.distribution.domain.PriceQueryResult;
import com.lin.distribution.domain.PriceTemplate;
import com.lin.distribution.domain.PriceTemplateSku;
import com.lin.distribution.domain.ProductSkuQuoteDetail;
import com.lin.distribution.mapper.PriceTemplateMapper;
import com.lin.distribution.mapper.PriceTemplateSkuMapper;
import com.lin.distribution.mapper.ProductSkuQuoteDetailMapper;
import com.lin.distribution.service.PriceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 取价服务实现（deepseek_redesign.md §5.3）
 * 优先级：配送点覆盖价 > 客户报价单 > 报价模板；均未命中返回空价（不得自动转免费/零价）。
 *
 * @author dsh
 */
@Service
@RequiredArgsConstructor
public class PriceQueryServiceImpl implements PriceQueryService {

    private final ProductSkuQuoteDetailMapper productSkuQuoteDetailMapper;
    private final PriceTemplateMapper priceTemplateMapper;
    private final PriceTemplateSkuMapper priceTemplateSkuMapper;

    @Override
    public PriceQueryResult queryPrice(Long customerId, Long deliveryPointId, Long skuId, LocalDate deliveryDate) {
        // 1. 客户报价（已发布 + 有效期区间内最新；原「配送点覆盖价」已随 delivery_sku_override 废弃，见 sql/s11）
        if (customerId != null) {
            ProductSkuQuoteDetail customerPrice = productSkuQuoteDetailMapper.selectActivePriceByCustomerAndSku(customerId, skuId, deliveryDate);
            if (customerPrice != null && customerPrice.getPrice() != null) {
                return PriceQueryResult.builder()
                        .skuId(skuId)
                        .price(customerPrice.getPrice())
                        .source(2)
                        .sourceId(customerPrice.getId())
                        .build();
            }
            // 3. 客户关联报价模板
            PriceTemplate template = priceTemplateMapper.selectTemplateByCustomerId(customerId);
            if (template != null) {
                PriceTemplateSku templateSku = priceTemplateSkuMapper.selectActivePriceByTemplateAndSku(template.getId(), skuId, deliveryDate);
                if (templateSku != null && templateSku.getUnitPrice() != null) {
                    return PriceQueryResult.builder()
                            .skuId(skuId)
                            .price(templateSku.getUnitPrice())
                            .source(3)
                            .sourceId(templateSku.getId())
                            .build();
                }
            }
        }
        // 未命中：返回空价
        return PriceQueryResult.empty(skuId);
    }
}

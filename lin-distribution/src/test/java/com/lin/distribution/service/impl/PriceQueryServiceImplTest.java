package com.lin.distribution.service.impl;

import com.lin.distribution.domain.PriceQueryResult;
import com.lin.distribution.domain.ProductSkuQuoteDetail;
import com.lin.distribution.mapper.ProductSkuQuoteDetailMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 取价口径自动化测试（客户端化ERP优化蓝图 W0-1「价格层级」简化后回归）
 * 仅使用客户正式报价；未命中返回空价（不得免费/零价，由文员手工定价）。
 * （原「配送点覆盖价」已随 delivery_sku_override 废弃，见 sql/s11；
 *   原「客户关联报价模板」已随 price_template 体系下线，见 sql/w01_price_simplify.sql）
 *
 * @author dsh
 */
@ExtendWith(MockitoExtension.class)
class PriceQueryServiceImplTest {

    @Mock
    private ProductSkuQuoteDetailMapper productSkuQuoteDetailMapper;

    @InjectMocks
    private PriceQueryServiceImpl priceQueryService;

    private static final Long CUSTOMER_ID = 100L;
    private static final Long POINT_ID = 200L;
    private static final Long SKU_ID = 300L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 16);

    @Test
    void 命中客户报价时返回报价来源() {
        ProductSkuQuoteDetail customerPrice = new ProductSkuQuoteDetail();
        customerPrice.setId(2L);
        customerPrice.setPrice(new BigDecimal("10.00"));
        when(productSkuQuoteDetailMapper.selectActivePriceByCustomerAndSku(CUSTOMER_ID, SKU_ID, DATE)).thenReturn(customerPrice);

        PriceQueryResult result = priceQueryService.queryPrice(CUSTOMER_ID, POINT_ID, SKU_ID, DATE);

        assertEquals(0, new BigDecimal("10.00").compareTo(result.getPrice()));
        assertEquals(PriceQueryResult.SOURCE_CUSTOMER_QUOTE, result.getSource());
        assertEquals(2L, result.getSourceId());
    }

    @Test
    void 未命中客户报价返回空价() {
        when(productSkuQuoteDetailMapper.selectActivePriceByCustomerAndSku(CUSTOMER_ID, SKU_ID, DATE)).thenReturn(null);

        PriceQueryResult result = priceQueryService.queryPrice(CUSTOMER_ID, POINT_ID, SKU_ID, DATE);

        assertNull(result.getPrice());
        assertNull(result.getSource());
    }

    @Test
    void 零价报价视为未命中() {
        // 蓝图「零价报价」：明细单价为 0 视为无报价，必须返回空价走手工定价
        ProductSkuQuoteDetail zeroPrice = new ProductSkuQuoteDetail();
        zeroPrice.setId(3L);
        zeroPrice.setPrice(BigDecimal.ZERO);
        when(productSkuQuoteDetailMapper.selectActivePriceByCustomerAndSku(CUSTOMER_ID, SKU_ID, DATE)).thenReturn(zeroPrice);

        PriceQueryResult result = priceQueryService.queryPrice(CUSTOMER_ID, POINT_ID, SKU_ID, DATE);

        assertNull(result.getPrice());
        assertNull(result.getSource());
    }

    @Test
    void 配送点参数不再参与取价_客户为空直接返回空价() {
        // 价格层级简化后 deliveryPointId 仅保留参数兼容；customerId 为空时不得触库
        PriceQueryResult result = priceQueryService.queryPrice(null, POINT_ID, SKU_ID, DATE);

        assertNull(result.getPrice());
        verifyNoInteractions(productSkuQuoteDetailMapper);
    }
}

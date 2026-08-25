package com.lin.distribution.service.impl;

import com.lin.distribution.domain.PriceQueryResult;
import com.lin.distribution.domain.PriceTemplate;
import com.lin.distribution.domain.PriceTemplateSku;
import com.lin.distribution.domain.ProductSkuQuoteDetail;
import com.lin.distribution.mapper.PriceTemplateMapper;
import com.lin.distribution.mapper.PriceTemplateSkuMapper;
import com.lin.distribution.mapper.ProductSkuQuoteDetailMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 取价优先级自动化测试（DESIGN.md 验收标准 1）
 * 客户报价 > 客户关联报价模板；未命中返回空价（不得免费/零价）。
 * （原「配送点覆盖价」优先级已随 delivery_sku_override 废弃移除，见 sql/s11）
 */
@ExtendWith(MockitoExtension.class)
class PriceQueryServiceImplTest {

    @Mock
    private ProductSkuQuoteDetailMapper productSkuQuoteDetailMapper;
    @Mock
    private PriceTemplateMapper priceTemplateMapper;
    @Mock
    private PriceTemplateSkuMapper priceTemplateSkuMapper;

    @InjectMocks
    private PriceQueryServiceImpl priceQueryService;

    private static final Long CUSTOMER_ID = 100L;
    private static final Long POINT_ID = 200L;
    private static final Long SKU_ID = 300L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 16);

    @BeforeEach
    void setUp() {
        lenient().when(productSkuQuoteDetailMapper.selectActivePriceByCustomerAndSku(CUSTOMER_ID, SKU_ID, DATE)).thenReturn(null);
        lenient().when(priceTemplateMapper.selectTemplateByCustomerId(CUSTOMER_ID)).thenReturn(null);
    }

    @Test
    void 配送点未命中时回退客户报价() {
        ProductSkuQuoteDetail customerPrice = new ProductSkuQuoteDetail();
        customerPrice.setId(2L);
        customerPrice.setPrice(new BigDecimal("10.00"));
        when(productSkuQuoteDetailMapper.selectActivePriceByCustomerAndSku(CUSTOMER_ID, SKU_ID, DATE)).thenReturn(customerPrice);

        PriceQueryResult result = priceQueryService.queryPrice(CUSTOMER_ID, POINT_ID, SKU_ID, DATE);

        assertEquals(0, new BigDecimal("10.00").compareTo(result.getPrice()));
        assertEquals(2, result.getSource());
        verify(priceTemplateMapper, never()).selectTemplateByCustomerId(any());
    }

    @Test
    void 客户报价未命中时回退客户模板() {
        PriceTemplate template = new PriceTemplate();
        template.setId(3L);
        when(priceTemplateMapper.selectTemplateByCustomerId(CUSTOMER_ID)).thenReturn(template);
        PriceTemplateSku templateSku = new PriceTemplateSku();
        templateSku.setId(4L);
        templateSku.setUnitPrice(new BigDecimal("8.80"));
        when(priceTemplateSkuMapper.selectActivePriceByTemplateAndSku(3L, SKU_ID, DATE)).thenReturn(templateSku);

        PriceQueryResult result = priceQueryService.queryPrice(CUSTOMER_ID, POINT_ID, SKU_ID, DATE);

        assertEquals(0, new BigDecimal("8.80").compareTo(result.getPrice()));
        assertEquals(3, result.getSource());
    }

    @Test
    void 全部未命中返回空价() {
        PriceQueryResult result = priceQueryService.queryPrice(CUSTOMER_ID, POINT_ID, SKU_ID, DATE);

        assertNull(result.getPrice());
        assertNull(result.getSource());
    }

    @Test
    void 查询顺序严格按优先级() {
        priceQueryService.queryPrice(CUSTOMER_ID, POINT_ID, SKU_ID, DATE);

        InOrder inOrder = inOrder(productSkuQuoteDetailMapper, priceTemplateMapper);
        inOrder.verify(productSkuQuoteDetailMapper).selectActivePriceByCustomerAndSku(CUSTOMER_ID, SKU_ID, DATE);
        inOrder.verify(priceTemplateMapper).selectTemplateByCustomerId(CUSTOMER_ID);
    }
}

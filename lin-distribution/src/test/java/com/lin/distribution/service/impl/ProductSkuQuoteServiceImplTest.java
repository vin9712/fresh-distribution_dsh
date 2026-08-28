package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.ProductSkuQuoteStatus;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.domain.ProductSkuQuote;
import com.lin.distribution.domain.ProductSkuQuoteDetail;
import com.lin.distribution.dto.ProductSkuQuoteUpdateStatusDTO;
import com.lin.distribution.mapper.BizCodeSeqMapper;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.CustomerSkuMappingMapper;
import com.lin.distribution.mapper.ProductAliasMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.mapper.ProductSkuQuoteDetailMapper;
import com.lin.distribution.mapper.ProductSkuQuoteMapper;
import com.lin.distribution.mapper.TempProductMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.ProductCreationService;
import com.lin.distribution.service.TempProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报价状态机与发布重叠校验测试（蓝图 W0-1/「报价纠错」「报价冲突」）
 *
 * @author dsh
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductSkuQuoteServiceImplTest {

    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private ProductSkuMapper productSkuMapper;
    @Mock
    private ProductSkuQuoteMapper productSkuQuoteMapper;
    @Mock
    private ProductSkuQuoteDetailMapper productSkuQuoteDetailMapper;
    @Mock
    private BizCodeService bizCodeService;
    @Mock
    private TempProductMapper tempProductMapper;
    @Mock
    private ProductAliasMapper productAliasMapper;
    @Mock
    private CustomerSkuMappingMapper customerSkuMappingMapper;
    @Mock
    private ProductCreationService productCreationService;
    @Mock
    private TempProductService tempProductService;
    @Mock
    private BizCodeSeqMapper bizCodeSeqMapper;

    @InjectMocks
    private ProductSkuQuoteServiceImpl service;

    private static final Long QUOTE_ID = 10L;
    private static final Long CUSTOMER_ID = 100L;
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    private ProductSkuQuote draftQuote() {
        ProductSkuQuote quote = new ProductSkuQuote();
        quote.setId(QUOTE_ID);
        quote.setCustomerId(CUSTOMER_ID);
        quote.setCode("BJ20260901001");
        quote.setStatus(ProductSkuQuoteStatus.NEW.getCode());
        quote.setEffectiveStartDate(START);
        quote.setEffectiveEndDate(END);
        return quote;
    }

    private ProductSkuQuoteDetail detail(Long quoteId, Long skuId, String price) {
        ProductSkuQuoteDetail d = new ProductSkuQuoteDetail();
        d.setQuoteId(quoteId);
        d.setSkuId(skuId);
        d.setPrice(new BigDecimal(price));
        return d;
    }

    @BeforeEach
    void setUp() {
        // 默认无重叠已发布报价
        lenient().when(productSkuQuoteMapper.selectOverlappingPublishedQuotes(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void 已发布报价不可撤回为草稿() {
        ProductSkuQuote published = draftQuote();
        published.setStatus(ProductSkuQuoteStatus.PUBLISHED.getCode());
        when(productSkuQuoteMapper.selectProductSkuQuoteById(QUOTE_ID)).thenReturn(published);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateQuoteStatus(
                ProductSkuQuoteUpdateStatusDTO.builder().quoteId(QUOTE_ID).status(ProductSkuQuoteStatus.NEW).build()));

        assertTrue(ex.getMessage().contains("不可撤回为草稿"));
        verify(productSkuQuoteMapper, never()).updateProductSkuQuote(any());
    }

    @Test
    void 已作废报价不可重复作废() {
        ProductSkuQuote invalid = draftQuote();
        invalid.setStatus(ProductSkuQuoteStatus.INVALID.getCode());
        when(productSkuQuoteMapper.selectProductSkuQuoteById(QUOTE_ID)).thenReturn(invalid);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateQuoteStatus(
                ProductSkuQuoteUpdateStatusDTO.builder().quoteId(QUOTE_ID).status(ProductSkuQuoteStatus.INVALID).build()));

        assertTrue(ex.getMessage().contains("已作废"));
    }

    @Test
    void 草稿可作废() {
        ProductSkuQuote draft = draftQuote();
        when(productSkuQuoteMapper.selectProductSkuQuoteById(QUOTE_ID)).thenReturn(draft);

        service.updateQuoteStatus(ProductSkuQuoteUpdateStatusDTO.builder()
                .quoteId(QUOTE_ID).status(ProductSkuQuoteStatus.INVALID).build());

        assertEquals(ProductSkuQuoteStatus.INVALID.getCode(), draft.getStatus());
        verify(productSkuQuoteMapper).updateProductSkuQuote(draft);
    }

    @Test
    void 发布时同客户同商品有效期重叠则拒绝并列出冲突商品() {
        ProductSkuQuote draft = draftQuote();
        when(productSkuQuoteMapper.selectProductSkuQuoteById(QUOTE_ID)).thenReturn(draft);

        ProductSkuQuote other = new ProductSkuQuote();
        other.setId(20L);
        other.setCode("BJ20260901002");
        other.setEffectiveStartDate(LocalDate.of(2026, 9, 15));
        other.setEffectiveEndDate(LocalDate.of(2026, 10, 15));
        when(productSkuQuoteMapper.selectOverlappingPublishedQuotes(CUSTOMER_ID, QUOTE_ID, START, END))
                .thenReturn(List.of(other));

        when(productSkuQuoteDetailMapper.selectProductSkuQuoteDetailListByQuoteId(QUOTE_ID))
                .thenReturn(List.of(detail(QUOTE_ID, 1L, "3.00"), detail(QUOTE_ID, 2L, "4.00")));
        when(productSkuQuoteDetailMapper.selectProductSkuQuoteDetailListByQuoteId(20L))
                .thenReturn(List.of(detail(20L, 2L, "4.50"), detail(20L, 3L, "5.00")));

        ProductSku sku2 = new ProductSku();
        sku2.setId(2L);
        sku2.setName("黄心土豆/5斤装");
        when(productSkuMapper.selectProductSkuById(2L)).thenReturn(sku2);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateQuoteStatus(
                ProductSkuQuoteUpdateStatusDTO.builder().quoteId(QUOTE_ID).status(ProductSkuQuoteStatus.PUBLISHED).build()));

        assertTrue(ex.getMessage().contains("重叠冲突"));
        assertTrue(ex.getMessage().contains("BJ20260901002"));
        assertTrue(ex.getMessage().contains("黄心土豆"));
        verify(productSkuQuoteMapper, never()).updateProductSkuQuote(any());
    }

    @Test
    void 发布时无重叠则正常发布() {
        ProductSkuQuote draft = draftQuote();
        when(productSkuQuoteMapper.selectProductSkuQuoteById(QUOTE_ID)).thenReturn(draft);
        when(productSkuQuoteDetailMapper.selectProductSkuQuoteDetailListByQuoteId(QUOTE_ID))
                .thenReturn(List.of(detail(QUOTE_ID, 1L, "3.00")));

        service.updateQuoteStatus(ProductSkuQuoteUpdateStatusDTO.builder()
                .quoteId(QUOTE_ID).status(ProductSkuQuoteStatus.PUBLISHED).build());

        assertEquals(ProductSkuQuoteStatus.PUBLISHED.getCode(), draft.getStatus());
        verify(productSkuQuoteMapper).updateProductSkuQuote(draft);
    }

    @Test
    void 发布时重叠但商品不交叉则允许发布() {
        // 蓝图口径：同客户、同商品的已发布报价才构成冲突；仅区间重叠但 SKU 不交叉可发布
        ProductSkuQuote draft = draftQuote();
        when(productSkuQuoteMapper.selectProductSkuQuoteById(QUOTE_ID)).thenReturn(draft);

        ProductSkuQuote other = new ProductSkuQuote();
        other.setId(20L);
        other.setCode("BJ20260901002");
        when(productSkuQuoteMapper.selectOverlappingPublishedQuotes(eq(CUSTOMER_ID), eq(QUOTE_ID), any(), any()))
                .thenReturn(List.of(other));

        when(productSkuQuoteDetailMapper.selectProductSkuQuoteDetailListByQuoteId(QUOTE_ID))
                .thenReturn(List.of(detail(QUOTE_ID, 1L, "3.00")));
        when(productSkuQuoteDetailMapper.selectProductSkuQuoteDetailListByQuoteId(20L))
                .thenReturn(List.of(detail(20L, 2L, "4.50")));

        service.updateQuoteStatus(ProductSkuQuoteUpdateStatusDTO.builder()
                .quoteId(QUOTE_ID).status(ProductSkuQuoteStatus.PUBLISHED).build());

        assertEquals(ProductSkuQuoteStatus.PUBLISHED.getCode(), draft.getStatus());
    }
}

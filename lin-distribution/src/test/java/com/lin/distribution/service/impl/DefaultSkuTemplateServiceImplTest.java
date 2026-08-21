package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.CustomerSku;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.mapper.CustomerSkuMapper;
import com.lin.distribution.mapper.DefaultSkuTemplateItemMapper;
import com.lin.distribution.mapper.DefaultSkuTemplateMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.service.BizCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 默认SKU模板批量赋值单测（deepseek_redesign.md §5.1）
 */
@ExtendWith(MockitoExtension.class)
class DefaultSkuTemplateServiceImplTest {

    @Mock
    private DefaultSkuTemplateMapper defaultSkuTemplateMapper;
    @Mock
    private DefaultSkuTemplateItemMapper defaultSkuTemplateItemMapper;
    @Mock
    private CustomerSkuMapper customerSkuMapper;
    @Mock
    private ProductSkuMapper productSkuMapper;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private DefaultSkuTemplateServiceImpl templateService;

    private ProductSku sku(Long id, String unit) {
        ProductSku s = new ProductSku();
        s.setId(id);
        s.setUnit(unit);
        return s;
    }

    private CustomerSku owned(Long skuId, Integer follow) {
        CustomerSku cs = new CustomerSku();
        cs.setId(skuId);
        cs.setSkuId(skuId);
        cs.setIsFollowDefault(follow);
        return cs;
    }

    @Test
    void 仅新增策略_已存在不更新() {
        when(productSkuMapper.selectProductSkuById(10L)).thenReturn(sku(10L, "斤"));
        when(customerSkuMapper.selectCustomerSkuList(any())).thenReturn(Collections.singletonList(owned(10L, 1)));

        int affected = templateService.batchAssign(Collections.singletonList(10L), Collections.singletonList(1L), 1, null);

        assertEquals(0, affected);
        verify(customerSkuMapper, never()).insertCustomerSku(any());
        verify(customerSkuMapper, never()).updateCustomerSku(any());
    }

    @Test
    void 仅新增策略_不存在则插入并跟随默认() {
        when(productSkuMapper.selectProductSkuById(10L)).thenReturn(sku(10L, "斤"));
        when(customerSkuMapper.selectCustomerSkuList(any())).thenReturn(Collections.emptyList());
        when(bizCodeService.nextCustomerSkuCode(1L)).thenReturn("C1000001");

        int affected = templateService.batchAssign(Collections.singletonList(10L), Collections.singletonList(1L), 1, 5L);

        assertEquals(1, affected);
        ArgumentCaptor<CustomerSku> captor = ArgumentCaptor.forClass(CustomerSku.class);
        verify(customerSkuMapper).insertCustomerSku(captor.capture());
        assertEquals(1, captor.getValue().getIsFollowDefault());
        assertEquals(5L, captor.getValue().getSourceTemplateId());
        assertEquals("斤", captor.getValue().getUnit());
    }

    @Test
    void 覆盖未个性化策略_仅更新跟随默认的商品() {
        when(productSkuMapper.selectProductSkuById(10L)).thenReturn(sku(10L, "斤"));
        when(productSkuMapper.selectProductSkuById(20L)).thenReturn(sku(20L, "箱"));
        when(customerSkuMapper.selectCustomerSkuList(any()))
                .thenReturn(Arrays.asList(owned(10L, 1), owned(20L, 0)));

        int affected = templateService.batchAssign(Arrays.asList(10L, 20L), Collections.singletonList(1L), 2, null);

        assertEquals(1, affected);
        ArgumentCaptor<CustomerSku> captor = ArgumentCaptor.forClass(CustomerSku.class);
        verify(customerSkuMapper).updateCustomerSku(captor.capture());
        assertEquals(10L, captor.getValue().getId());
        assertEquals(1, captor.getValue().getIsFollowDefault());
    }

    @Test
    void 全部覆盖策略_已个性化也更新() {
        when(productSkuMapper.selectProductSkuById(10L)).thenReturn(sku(10L, "斤"));
        when(customerSkuMapper.selectCustomerSkuList(any())).thenReturn(Collections.singletonList(owned(10L, 0)));

        int affected = templateService.batchAssign(Collections.singletonList(10L), Collections.singletonList(1L), 3, null);

        assertEquals(1, affected);
        verify(customerSkuMapper).updateCustomerSku(any());
    }

    @Test
    void 非法策略抛异常() {
        assertThrows(ServiceException.class,
                () -> templateService.batchAssign(Collections.singletonList(10L), Collections.singletonList(1L), 9, null));
    }
}

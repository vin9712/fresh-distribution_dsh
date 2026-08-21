package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.CustomerSku;
import com.lin.distribution.domain.DeliverySkuOverride;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.mapper.CustomerSkuMapper;
import com.lin.distribution.mapper.DeliverySkuOverrideMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.service.BizCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户商品服务单测（deepseek_redesign.md §3.7 / §5.2）
 */
@ExtendWith(MockitoExtension.class)
class CustomerSkuServiceImplTest {

    @Mock
    private CustomerSkuMapper customerSkuMapper;
    @Mock
    private ProductSkuMapper productSkuMapper;
    @Mock
    private DeliverySkuOverrideMapper deliverySkuOverrideMapper;
    @Mock
    private BizCodeService bizCodeService;

    @InjectMocks
    private CustomerSkuServiceImpl customerSkuService;

    @Test
    void 新增时同客户同SKU重复抛异常() {
        CustomerSku cs = new CustomerSku();
        cs.setCustomerId(1L);
        cs.setSkuId(10L);
        when(customerSkuMapper.selectByCustomerAndSku(1L, 10L)).thenReturn(new CustomerSku());

        assertThrows(ServiceException.class, () -> customerSkuService.insertCustomerSku(cs));
    }

    @Test
    void 新增时单位默认继承标准SKU且生成客户编码() {
        CustomerSku cs = new CustomerSku();
        cs.setCustomerId(1L);
        cs.setSkuId(10L);
        when(customerSkuMapper.selectByCustomerAndSku(1L, 10L)).thenReturn(null);
        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setUnit("斤");
        when(productSkuMapper.selectProductSkuById(10L)).thenReturn(sku);
        when(bizCodeService.nextCustomerSkuCode(1L)).thenReturn("C1000001");

        customerSkuService.insertCustomerSku(cs);

        assertEquals("斤", cs.getUnit());
        assertEquals("C1000001", cs.getCustomerCode());
        verify(customerSkuMapper).insertCustomerSku(cs);
    }

    @Test
    void 客户商品列表合并配送点覆盖_隐藏SKU被移除() {
        CustomerSku cs1 = new CustomerSku();
        cs1.setSkuId(10L);
        CustomerSku cs2 = new CustomerSku();
        cs2.setSkuId(20L);
        CustomerSku query = new CustomerSku();
        query.setCustomerId(1L);
        when(customerSkuMapper.selectCustomerSkuList(any())).thenReturn(new java.util.ArrayList<>(Arrays.asList(cs1, cs2)));

        DeliverySkuOverride hide = new DeliverySkuOverride();
        hide.setSkuId(10L);
        hide.setIsAvailable(0);
        when(deliverySkuOverrideMapper.selectByPoint(200L)).thenReturn(Arrays.asList(hide));

        List<CustomerSku> result = customerSkuService.listCustomerProducts(1L, 200L, null);

        assertEquals(1, result.size());
        assertEquals(20L, result.get(0).getSkuId());
    }

    @Test
    void 客户商品列表合并配送点覆盖_别名价格覆盖() {
        CustomerSku cs = new CustomerSku();
        cs.setSkuId(10L);
        when(customerSkuMapper.selectCustomerSkuList(any())).thenReturn(Arrays.asList(cs));

        DeliverySkuOverride ov = new DeliverySkuOverride();
        ov.setSkuId(10L);
        ov.setIsAvailable(1);
        ov.setAliasOverride("客户叫法X");
        ov.setPriceOverride(new java.math.BigDecimal("5.5"));
        when(deliverySkuOverrideMapper.selectByPoint(200L)).thenReturn(Arrays.asList(ov));

        List<CustomerSku> result = customerSkuService.listCustomerProducts(1L, 200L, null);

        assertEquals("客户叫法X", result.get(0).getAlias());
        assertEquals(0, new java.math.BigDecimal("5.5").compareTo(result.get(0).getPriceOverride()));
        assertEquals(200L, result.get(0).getDeliveryPointId());
    }

    @Test
    void 无配送点时不做覆盖合并() {
        CustomerSku cs = new CustomerSku();
        cs.setSkuId(10L);
        when(customerSkuMapper.selectCustomerSkuList(any())).thenReturn(Arrays.asList(cs));

        List<CustomerSku> result = customerSkuService.listCustomerProducts(1L, null, null);

        assertEquals(1, result.size());
    }

    @Test
    void 关键字搜索时把配送点透传给Mapper以匹配覆盖别名() {
        CustomerSku cs = new CustomerSku();
        cs.setSkuId(10L);
        // 捕获传给 mapper 的查询条件
        final CustomerSku[] captured = new CustomerSku[1];
        when(customerSkuMapper.selectCustomerSkuList(any())).thenAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            return Arrays.asList(cs);
        });
        when(deliverySkuOverrideMapper.selectByPoint(200L)).thenReturn(new java.util.ArrayList<>());

        customerSkuService.listCustomerProducts(1L, 200L, "洋柿子");

        assertEquals(200L, captured[0].getDeliveryPointId());
        assertEquals("洋柿子", captured[0].getKeyword());
        assertEquals(1L, captured[0].getCustomerId());
    }
}

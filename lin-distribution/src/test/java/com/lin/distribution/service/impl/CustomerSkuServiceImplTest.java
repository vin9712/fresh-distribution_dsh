package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.CustomerSku;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.mapper.CustomerSkuMapper;
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
    void 客户商品列表按配送点白名单过滤() {
        // 白名单过滤已下推到 CustomerSkuMapper.xml（dept_id is null or dept_id = 配送点），
        // 服务层只负责透传查询参数，这里验证透传行为。
        CustomerSku cs = new CustomerSku();
        cs.setSkuId(10L);
        final CustomerSku[] captured = new CustomerSku[1];
        when(customerSkuMapper.selectCustomerSkuList(any())).thenAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            return new java.util.ArrayList<>(java.util.Collections.singletonList(cs));
        });

        List<CustomerSku> result = customerSkuService.listCustomerProducts(1L, 200L, null);

        assertEquals(1, result.size());
        assertEquals(200L, captured[0].getDeliveryPointId());
        assertEquals(1L, captured[0].getCustomerId());
    }

    @Test
    void 无配送点时不过滤商品池() {
        CustomerSku cs = new CustomerSku();
        cs.setSkuId(10L);
        when(customerSkuMapper.selectCustomerSkuList(any())).thenReturn(Arrays.asList(cs));

        List<CustomerSku> result = customerSkuService.listCustomerProducts(1L, null, null);

        assertEquals(1, result.size());
    }

    @Test
    void 关键字搜索时把配送点透传给Mapper用于白名单过滤() {
        CustomerSku cs = new CustomerSku();
        cs.setSkuId(10L);
        // 捕获传给 mapper 的查询条件
        final CustomerSku[] captured = new CustomerSku[1];
        when(customerSkuMapper.selectCustomerSkuList(any())).thenAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            return Arrays.asList(cs);
        });

        customerSkuService.listCustomerProducts(1L, 200L, "洋柿子");

        assertEquals(200L, captured[0].getDeliveryPointId());
        assertEquals("洋柿子", captured[0].getKeyword());
        assertEquals(1L, captured[0].getCustomerId());
    }
}

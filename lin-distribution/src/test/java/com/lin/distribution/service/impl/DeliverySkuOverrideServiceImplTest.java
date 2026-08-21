package com.lin.distribution.service.impl;

import com.lin.distribution.domain.DeliverySkuOverride;
import com.lin.distribution.mapper.DeliverySkuOverrideMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配送点覆盖服务单测（deepseek_redesign.md §3.8）
 */
@ExtendWith(MockitoExtension.class)
class DeliverySkuOverrideServiceImplTest {

    @Mock
    private DeliverySkuOverrideMapper deliverySkuOverrideMapper;

    @InjectMocks
    private DeliverySkuOverrideServiceImpl overrideService;

    @Test
    void 同配送点同SKU存在时更新而非插入() {
        DeliverySkuOverride exist = new DeliverySkuOverride();
        exist.setId(1L);
        when(deliverySkuOverrideMapper.selectDeliverySkuOverrideList(any())).thenReturn(Collections.singletonList(exist));

        DeliverySkuOverride input = new DeliverySkuOverride();
        input.setDeliveryPointId(200L);
        input.setSkuId(10L);
        input.setPriceOverride(new java.math.BigDecimal("8.80"));

        overrideService.saveDeliverySkuOverride(input);

        assertEquals(1L, input.getId());
        verify(deliverySkuOverrideMapper).updateDeliverySkuOverride(input);
    }

    @Test
    void 同配送点同SKU不存在时插入且默认可用() {
        when(deliverySkuOverrideMapper.selectDeliverySkuOverrideList(any())).thenReturn(Collections.emptyList());

        DeliverySkuOverride input = new DeliverySkuOverride();
        input.setDeliveryPointId(200L);
        input.setSkuId(10L);
        input.setPriceOverride(new java.math.BigDecimal("8.80"));

        overrideService.saveDeliverySkuOverride(input);

        assertEquals(Integer.valueOf(1), input.getIsAvailable());
        verify(deliverySkuOverrideMapper).insertDeliverySkuOverride(input);
    }
}

package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.CustomerSku;
import com.lin.distribution.domain.DeliverySkuOverride;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.mapper.CustomerSkuMapper;
import com.lin.distribution.mapper.DeliverySkuOverrideMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.CustomerSkuService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户商品服务实现（deepseek_redesign.md §3.7 / §5.1 / §5.2）
 *
 * @author dsh
 */
@Service
@RequiredArgsConstructor
public class CustomerSkuServiceImpl implements CustomerSkuService {

    private final CustomerSkuMapper customerSkuMapper;
    private final ProductSkuMapper productSkuMapper;
    private final DeliverySkuOverrideMapper deliverySkuOverrideMapper;
    private final BizCodeService bizCodeService;

    @Override
    public CustomerSku selectCustomerSkuById(Long id) {
        return customerSkuMapper.selectCustomerSkuById(id);
    }

    @Override
    public List<CustomerSku> selectCustomerSkuList(CustomerSku customerSku) {
        return customerSkuMapper.selectCustomerSkuList(customerSku);
    }

    @Override
    @Transactional
    public int insertCustomerSku(CustomerSku customerSku) {
        if (customerSku.getCustomerId() == null) {
            throw new ServiceException("客户id不能为空");
        }
        if (customerSku.getSkuId() == null) {
            throw new ServiceException("sku id不能为空");
        }
        // 唯一性：同一客户同一SKU只能有一条
        CustomerSku exist = customerSkuMapper.selectByCustomerAndSku(customerSku.getCustomerId(), customerSku.getSkuId());
        if (exist != null) {
            throw new ServiceException("该客户已存在此商品");
        }
        // unit 默认继承标准SKU单位
        if (StringUtils.isBlank(customerSku.getUnit())) {
            ProductSku sku = productSkuMapper.selectProductSkuById(customerSku.getSkuId());
            if (sku != null) {
                customerSku.setUnit(sku.getUnit());
            }
        }
        if (StringUtils.isBlank(customerSku.getCustomerCode())) {
            customerSku.setCustomerCode(bizCodeService.nextCustomerSkuCode(customerSku.getCustomerId()));
        }
        if (customerSku.getMinOrderQty() == null) {
            customerSku.setMinOrderQty(java.math.BigDecimal.ONE);
        }
        if (customerSku.getOrderStep() == null) {
            customerSku.setOrderStep(java.math.BigDecimal.ONE);
        }
        if (customerSku.getIsFollowDefault() == null) {
            customerSku.setIsFollowDefault(0);
        }
        if (customerSku.getStatus() == null) {
            customerSku.setStatus(1);
        }
        return customerSkuMapper.insertCustomerSku(customerSku);
    }

    @Override
    public int updateCustomerSku(CustomerSku customerSku) {
        if (customerSku.getId() == null) {
            throw new ServiceException("客户商品id不能为空");
        }
        // 仅更新传入字段；个性化（is_follow_default=0、清 source_template_id）由调用方显式传入
        return customerSkuMapper.updateCustomerSku(customerSku);
    }

    @Override
    public int deleteCustomerSkuByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return customerSkuMapper.deleteCustomerSkuByIds(ids);
    }

    @Override
    public List<CustomerSku> listCustomerProducts(Long customerId, Long deliveryPointId, String keyword) {
        CustomerSku query = new CustomerSku();
        query.setCustomerId(customerId);
        query.setKeyword(keyword);
        query.setDeliveryPointId(deliveryPointId);
        List<CustomerSku> list = customerSkuMapper.selectCustomerSkuList(query);

        // 合并配送点覆盖（deepseek_redesign.md §5.2）
        if (CollectionUtils.isNotEmpty(list) && deliveryPointId != null) {
            List<DeliverySkuOverride> overrides = deliverySkuOverrideMapper.selectByPoint(deliveryPointId);
            Map<Long, DeliverySkuOverride> overrideMap = overrides.stream()
                    .collect(Collectors.toMap(DeliverySkuOverride::getSkuId, o -> o, (a, b) -> a));
            list.removeIf(cs -> {
                DeliverySkuOverride ov = overrideMap.get(cs.getSkuId());
                return ov != null && ov.getIsAvailable() != null && ov.getIsAvailable() == 0;
            });
            for (CustomerSku cs : list) {
                DeliverySkuOverride ov = overrideMap.get(cs.getSkuId());
                if (ov != null) {
                    // 价格/别名覆盖（仅用于展示；交易价仍走取价引擎）
                    if (ov.getAliasOverride() != null) {
                        cs.setAlias(ov.getAliasOverride());
                    }
                    cs.setPriceOverride(ov.getPriceOverride());
                    cs.setDeliveryPointId(deliveryPointId);
                }
            }
        }
        return list;
    }
}

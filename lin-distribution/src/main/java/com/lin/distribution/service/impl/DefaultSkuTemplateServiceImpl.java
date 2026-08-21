package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.CustomerSku;
import com.lin.distribution.domain.DefaultSkuTemplate;
import com.lin.distribution.domain.DefaultSkuTemplateItem;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.mapper.CustomerSkuMapper;
import com.lin.distribution.mapper.DefaultSkuTemplateItemMapper;
import com.lin.distribution.mapper.DefaultSkuTemplateMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.DefaultSkuTemplateService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 默认SKU模板服务实现（deepseek_redesign.md §5.1 批量赋值）
 *
 * @author dsh
 */
@Service
@RequiredArgsConstructor
public class DefaultSkuTemplateServiceImpl implements DefaultSkuTemplateService {

    private final DefaultSkuTemplateMapper defaultSkuTemplateMapper;
    private final DefaultSkuTemplateItemMapper defaultSkuTemplateItemMapper;
    private final CustomerSkuMapper customerSkuMapper;
    private final ProductSkuMapper productSkuMapper;
    private final BizCodeService bizCodeService;

    @Override
    public DefaultSkuTemplate selectDefaultSkuTemplateById(Long id) {
        return defaultSkuTemplateMapper.selectDefaultSkuTemplateById(id);
    }

    @Override
    public List<DefaultSkuTemplate> selectDefaultSkuTemplateList(DefaultSkuTemplate defaultSkuTemplate) {
        return defaultSkuTemplateMapper.selectDefaultSkuTemplateList(defaultSkuTemplate);
    }

    @Override
    public List<DefaultSkuTemplateItem> selectTemplateItems(Long templateId) {
        return defaultSkuTemplateItemMapper.selectByTemplateId(templateId);
    }

    @Override
    @Transactional
    public int insertDefaultSkuTemplate(DefaultSkuTemplate defaultSkuTemplate, List<Long> skuIds) {
        if (defaultSkuTemplate.getName() == null || defaultSkuTemplate.getName().isBlank()) {
            throw new ServiceException("模板名称不能为空");
        }
        if (CollectionUtils.isEmpty(skuIds)) {
            throw new ServiceException("模板SKU不能为空");
        }
        if (defaultSkuTemplate.getStatus() == null) {
            defaultSkuTemplate.setStatus(1);
        }
        defaultSkuTemplateMapper.insertDefaultSkuTemplate(defaultSkuTemplate);
        saveItems(defaultSkuTemplate.getId(), skuIds);
        return 1;
    }

    @Override
    @Transactional
    public int updateDefaultSkuTemplate(DefaultSkuTemplate defaultSkuTemplate, List<Long> skuIds) {
        if (defaultSkuTemplate.getId() == null) {
            throw new ServiceException("模板id不能为空");
        }
        defaultSkuTemplateMapper.updateDefaultSkuTemplate(defaultSkuTemplate);
        if (skuIds != null) {
            defaultSkuTemplateItemMapper.deleteByTemplateId(defaultSkuTemplate.getId());
            if (CollectionUtils.isNotEmpty(skuIds)) {
                saveItems(defaultSkuTemplate.getId(), skuIds);
            }
        }
        return 1;
    }

    private void saveItems(Long templateId, List<Long> skuIds) {
        List<DefaultSkuTemplateItem> items = skuIds.stream().distinct()
                .map(skuId -> {
                    DefaultSkuTemplateItem item = new DefaultSkuTemplateItem();
                    item.setTemplateId(templateId);
                    item.setSkuId(skuId);
                    return item;
                })
                .collect(Collectors.toList());
        defaultSkuTemplateItemMapper.insertBatch(items);
    }

    @Override
    @Transactional
    public int deleteDefaultSkuTemplateByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (Long id : ids) {
            defaultSkuTemplateItemMapper.deleteByTemplateId(id);
        }
        return defaultSkuTemplateMapper.deleteDefaultSkuTemplateByIds(ids);
    }

    @Override
    @Transactional
    public int batchAssign(List<Long> skuIds, List<Long> customerIds, int strategy, Long templateId) {
        if (CollectionUtils.isEmpty(skuIds)) {
            throw new ServiceException("标准SKU集合不能为空");
        }
        if (CollectionUtils.isEmpty(customerIds)) {
            throw new ServiceException("目标客户不能为空");
        }
        if (strategy < 1 || strategy > 3) {
            throw new ServiceException("覆盖策略非法：1=仅新增 2=覆盖未个性化 3=全部覆盖");
        }
        // 去重
        List<Long> skuIdList = skuIds.stream().distinct().collect(Collectors.toList());
        List<Long> customerIdList = customerIds.stream().distinct().collect(Collectors.toList());

        // 预取标准SKU单位（为新增的客户商品设置默认单位）
        Map<Long, ProductSku> skuMap = new java.util.HashMap<>();
        for (Long skuId : skuIdList) {
            ProductSku sku = productSkuMapper.selectProductSkuById(skuId);
            if (sku == null) {
                throw new ServiceException("标准SKU不存在：" + skuId);
            }
            skuMap.put(skuId, sku);
        }

        int affected = 0;
        for (Long customerId : customerIdList) {
            // 该客户已拥有的客户商品（skuId -> record）
            List<CustomerSku> ownedList = customerSkuMapper.selectCustomerSkuList(buildQuery(customerId));
            Map<Long, CustomerSku> ownedMap = ownedList.stream()
                    .collect(Collectors.toMap(CustomerSku::getSkuId, Function.identity(), (a, b) -> a));

            for (Long skuId : skuIdList) {
                CustomerSku exist = ownedMap.get(skuId);
                if (exist == null) {
                    // 不存在：插入（is_follow_default=1，来源模板）
                    CustomerSku cs = new CustomerSku();
                    cs.setCustomerId(customerId);
                    cs.setSkuId(skuId);
                    cs.setCustomerCode(bizCodeService.nextCustomerSkuCode(customerId));
                    ProductSku sku = skuMap.get(skuId);
                    cs.setUnit(sku.getUnit());
                    cs.setMinOrderQty(BigDecimal.ONE);
                    cs.setOrderStep(BigDecimal.ONE);
                    cs.setIsFollowDefault(1);
                    cs.setSourceTemplateId(templateId);
                    cs.setStatus(1);
                    customerSkuMapper.insertCustomerSku(cs);
                    affected++;
                } else {
                    // 存在：按策略决定是否更新
                    boolean update = strategy == 3 || (strategy == 2 && exist.getIsFollowDefault() != null && exist.getIsFollowDefault() == 1);
                    if (update) {
                        CustomerSku updateCs = new CustomerSku();
                        updateCs.setId(exist.getId());
                        updateCs.setUnit(skuMap.get(skuId).getUnit());
                        updateCs.setMinOrderQty(BigDecimal.ONE);
                        updateCs.setOrderStep(BigDecimal.ONE);
                        updateCs.setIsFollowDefault(1);
                        updateCs.setSourceTemplateId(templateId);
                        updateCs.setStatus(1);
                        customerSkuMapper.updateCustomerSku(updateCs);
                        affected++;
                    }
                }
            }
        }
        return affected;
    }

    private CustomerSku buildQuery(Long customerId) {
        CustomerSku query = new CustomerSku();
        query.setCustomerId(customerId);
        return query;
    }
}

package com.lin.distribution.service;

import com.lin.distribution.domain.CustomerSku;

import java.util.List;

/**
 * 客户商品Service接口（customers_sku：客户商品池与个性化）
 *
 * @author dsh
 */
public interface CustomerSkuService {

    CustomerSku selectCustomerSkuById(Long id);

    List<CustomerSku> selectCustomerSkuList(CustomerSku customerSku);

    /**
     * 新增客户商品（自动生成 customer_code，unit 默认继承标准SKU）
     */
    int insertCustomerSku(CustomerSku customerSku);

    /**
     * 个性化修改（自动置 is_follow_default=0、清空 source_template_id）
     */
    int updateCustomerSku(CustomerSku customerSku);

    int deleteCustomerSkuByIds(Long[] ids);

    /**
     * 客户商品列表（合并配送点覆盖信息）：
     * 覆盖 is_available=0 隐藏；否则用 price_override/alias_override 覆盖显示
     *
     * @param customerId     客户ID
     * @param deliveryPointId 配送点ID（可空）
     * @param keyword        搜索关键字（可空）
     * @return 客户商品列表（含配送点覆盖字段）
     */
    List<CustomerSku> listCustomerProducts(Long customerId, Long deliveryPointId, String keyword);
}

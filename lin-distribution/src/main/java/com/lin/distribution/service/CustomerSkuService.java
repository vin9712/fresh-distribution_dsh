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
     * 粘贴文本快速同步客户商品：每行一条，支持「客户叫法=内部商品名」或直接「商品名」。
     * 自动按名称/助记码匹配内部 SKU 库，重复跳过，返回导入报告。
     *
     * @param customerId      客户ID
     * @param text            粘贴的商品清单文本
     * @param unmatchedToTemp 未匹配到标准SKU时是否自动转临时商品
     * @return 同步结果报告
     */
    String syncCustomerSkuText(Long customerId, String text, Boolean unmatchedToTemp);

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

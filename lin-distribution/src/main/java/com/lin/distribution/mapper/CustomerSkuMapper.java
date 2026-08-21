package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.CustomerSku;
import org.apache.ibatis.annotations.Param;

/**
 * 客户商品Mapper接口
 *
 * @author dsh
 */
public interface CustomerSkuMapper {

    CustomerSku selectCustomerSkuById(Long id);

    /**
     * 分页/条件查询客户商品（含标准SKU冗余信息）
     */
    List<CustomerSku> selectCustomerSkuList(CustomerSku customerSku);

    /**
     * 按客户+SKU查询（唯一性判断）
     */
    CustomerSku selectByCustomerAndSku(@Param("customerId") Long customerId, @Param("skuId") Long skuId);

    /**
     * 按客户+关键字搜索（匹配标准SKU名/助记码/全局别名/客户别名）
     */
    List<CustomerSku> selectByKeyword(@Param("customerId") Long customerId, @Param("keyword") String keyword);

    /**
     * 批量查询客户已拥有的SKU ID集合
     */
    List<Long> selectSkuIdsByCustomer(@Param("customerId") Long customerId);

    int insertCustomerSku(CustomerSku customerSku);

    int updateCustomerSku(CustomerSku customerSku);

    int deleteCustomerSkuById(Long id);

    int deleteCustomerSkuByIds(Long[] ids);
}

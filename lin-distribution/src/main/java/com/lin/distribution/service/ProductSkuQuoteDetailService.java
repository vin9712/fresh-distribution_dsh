package com.lin.distribution.service;

import com.lin.distribution.domain.ProductSkuQuoteDetail;

import java.util.List;

/**
 * 商品报价明细Service接口
 *
 * @author lin
 * @date 2024-11-15
 */
public interface ProductSkuQuoteDetailService {
    /**
     * 查询商品报价明细
     *
     * @param id 商品报价明细主键
     * @return 商品报价明细
     */
    ProductSkuQuoteDetail selectProductSkuQuoteDetailById(Long id);

    /**
     * 查询商品报价明细列表
     *
     * @param productSkuQuoteDetail 商品报价明细
     * @return 商品报价明细集合
     */
    List<ProductSkuQuoteDetail> selectProductSkuQuoteDetailList(ProductSkuQuoteDetail productSkuQuoteDetail);

    /**
     * 查询客户报价明细列表（含最新商品信息）
     * @param customerId
     * @param quoteId
     * @return
     */
    List<ProductSkuQuoteDetail> customerQuoteDetailList(Long customerId, Long quoteId);

    /**
     * 新增商品报价明细
     *
     * @param productSkuQuoteDetail 商品报价明细
     * @return 结果
     */
    int insertProductSkuQuoteDetail(ProductSkuQuoteDetail productSkuQuoteDetail);

    /**
     * 修改商品报价明细
     *
     * @param productSkuQuoteDetail 商品报价明细
     * @return 结果
     */
    int updateProductSkuQuoteDetail(ProductSkuQuoteDetail productSkuQuoteDetail);

    /**
     * 批量删除商品报价明细
     *
     * @param ids 需要删除的商品报价明细主键集合
     * @return 结果
     */
    int deleteProductSkuQuoteDetailByIds(Long[] ids);

    /**
     * 删除商品报价明细信息
     *
     * @param id 商品报价明细主键
     * @return 结果
     */
    int deleteProductSkuQuoteDetailById(Long id);
}

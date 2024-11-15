package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.domain.ProductSkuQuote;

/**
 * 商品报价Service接口
 *
 * @author lin
 * @date 2024-11-14
 */
public interface ProductSkuQuoteService {
    /**
     * 查询商品报价
     *
     * @param id 商品报价主键
     * @return 商品报价
     */
    ProductSkuQuote selectProductSkuQuoteById(Long id);

    /**
     * 查询商品报价列表
     *
     * @param productSkuQuote 商品报价
     * @return 商品报价集合
     */
    List<ProductSkuQuote> selectProductSkuQuoteList(ProductSkuQuote productSkuQuote);

    /**
     * 新增商品报价
     *
     * @param productSkuQuote 商品报价
     * @return 结果
     */
    int insertProductSkuQuote(ProductSkuQuote productSkuQuote);

    /**
     * 修改商品报价
     *
     * @param productSkuQuote 商品报价
     * @return 结果
     */
    int updateProductSkuQuote(ProductSkuQuote productSkuQuote);

    /**
     * 批量删除商品报价
     *
     * @param ids 需要删除的商品报价主键集合
     * @return 结果
     */
    int deleteProductSkuQuoteByIds(Long[] ids);

    /**
     * 删除商品报价信息
     *
     * @param id 商品报价主键
     * @return 结果
     */
    int deleteProductSkuQuoteById(Long id);

    /**
     * 生成商品报价单号
     * @return
     */
    String generateSkuQuoteNo(Boolean refresh);
}

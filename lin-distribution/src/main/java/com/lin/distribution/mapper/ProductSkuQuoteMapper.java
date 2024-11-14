package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.ProductSkuQuote;

/**
 * 商品报价Mapper接口
 *
 * @author lin
 * @date 2024-11-14
 */
public interface ProductSkuQuoteMapper {
    /**
     * 查询商品报价
     *
     * @param id 商品报价主键
     * @return 商品报价
     */
    ProductSkuQuote selectProductSkuQuoteById(Long id);

    /**
     * 根据code查询商品报价
     * @param code
     * @return
     */
    ProductSkuQuote selectProductSkuQuoteByCode(String code);

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
     * 删除商品报价
     *
     * @param id 商品报价主键
     * @return 结果
     */
    int deleteProductSkuQuoteById(Long id);

    /**
     * 批量删除商品报价
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteProductSkuQuoteByIds(Long[] ids);
}

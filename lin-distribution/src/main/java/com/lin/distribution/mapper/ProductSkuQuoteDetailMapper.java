package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.ProductSkuQuoteDetail;

/**
 * 商品报价明细Mapper接口
 *
 * @author lin
 * @date 2024-11-15
 */
public interface ProductSkuQuoteDetailMapper {
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


    List<ProductSkuQuoteDetail> selectProductSkuQuoteDetailListByQuoteId(Long quoteId);

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
     * 删除商品报价明细
     *
     * @param id 商品报价明细主键
     * @return 结果
     */
    int deleteProductSkuQuoteDetailById(Long id);

    /**
     * 根据 quoteId 删除商品报价明细
     * @param quoteId
     * @return
     */
    int deleteProductSkuQuoteDetailByQuoteId(Long quoteId);

    /**
     * 批量删除商品报价明细
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteProductSkuQuoteDetailByIds(Long[] ids);
}

package com.lin.distribution.service;

import com.lin.distribution.domain.ProductAlias;

import java.util.List;

/**
 * 商品全局别名Service接口
 *
 * @author lin
 * @date 2024-11-20
 */
public interface ProductAliasService {
    /**
     * 查询商品全局别名
     *
     * @param id 商品全局别名主键
     * @return 商品全局别名
     */
    ProductAlias selectProductAliasById(Long id);

    /**
     * 查询商品全局别名列表
     *
     * @param productAlias 商品全局别名
     * @return 商品全局别名集合
     */
    List<ProductAlias> selectProductAliasList(ProductAlias productAlias);

    /**
     * 按关键词检索别名（LIKE alias 或类型匹配），供录单检索
     *
     * @param keyword 关键词
     * @return 商品全局别名集合
     */
    List<ProductAlias> listByKeyword(String keyword);

    /**
     * 新增商品全局别名
     *
     * @param productAlias 商品全局别名
     * @return 结果
     */
    int insertProductAlias(ProductAlias productAlias);

    /**
     * 修改商品全局别名
     *
     * @param productAlias 商品全局别名
     * @return 结果
     */
    int updateProductAlias(ProductAlias productAlias);

    /**
     * 批量删除商品全局别名
     *
     * @param ids 需要删除的商品全局别名主键集合
     * @return 结果
     */
    int deleteProductAliasByIds(Long[] ids);
}

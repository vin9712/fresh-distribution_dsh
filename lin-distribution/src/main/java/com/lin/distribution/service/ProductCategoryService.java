package com.lin.distribution.service;

import com.lin.distribution.domain.ProductCategory;

import java.util.List;

/**
 * 商品分类Service接口
 *
 * @author lin
 * @date 2024-11-02
 */
public interface ProductCategoryService {
    /**
     * 查询商品分类
     *
     * @param id 商品分类主键
     * @return 商品分类
     */
    ProductCategory selectProductCategoryById(Long id);

    /**
     * 查询商品分类列表
     *
     * @param productCategory 商品分类
     * @return 商品分类集合
     */
    List<ProductCategory> selectProductCategoryList(ProductCategory productCategory);

    /**
     * 新增商品分类
     *
     * @param productCategory 商品分类
     * @return 结果
     */
    int insertProductCategory(ProductCategory productCategory);

    /**
     * 修改商品分类
     *
     * @param productCategory 商品分类
     * @return 结果
     */
    int updateProductCategory(ProductCategory productCategory);

    /**
     * 批量删除商品分类
     *
     * @param ids 需要删除的商品分类主键集合
     * @return 结果
     */
    int deleteProductCategoryByIds(Long[] ids);

    /**
     * 删除商品分类信息
     *
     * @param id 商品分类主键
     * @return 结果
     */
    int deleteProductCategoryById(Long id);

    /**
     * 获取新增商品分类排序
     *
     * @return
     */
    Long getNextProductCategorySort(Long id);

    /**
     * 获取新增商品分类编码
     * @return
     */
    String generateCode(Long parentId);
}

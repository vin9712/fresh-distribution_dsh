package com.lin.distribution.mapper.mapper;

import com.lin.distribution.domain.ProductCategory;

import java.util.List;

/**
 * 商品分类Mapper接口
 *
 * @author lin
 * @date 2024-11-02
 */
public interface ProductCategoryMapper {
    /**
     * 查询商品分类
     *
     * @param id 商品分类主键
     * @return 商品分类
     */
    ProductCategory selectProductCategoryById(Long id);

    /**
     * 查询商品分类
     *
     * @param name 商品分类名
     * @return 商品分类
     */
    List<ProductCategory> selectProductCategoryByName(String name);

    /**
     * 查询商品分类列表
     *
     * @param productCategory 商品分类
     * @return 商品分类集合
     */
    List<ProductCategory> selectProductCategoryList(ProductCategory productCategory);

    /**
     * 查询商品分类列表数量
     *
     * @param productCategory 商品分类
     * @return 商品分类集合数
     */
    Long selectProductCategoryCount(ProductCategory productCategory);

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
     * 删除商品分类
     *
     * @param id 商品分类主键
     * @return 结果
     */
    int deleteProductCategoryById(Long id);

    /**
     * 批量删除商品分类
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteProductCategoryByIds(Long[] ids);
}

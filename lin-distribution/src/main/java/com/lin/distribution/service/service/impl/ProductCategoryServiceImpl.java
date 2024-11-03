package com.lin.distribution.service.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.domain.ProductCategory;
import com.lin.distribution.mapper.mapper.ProductCategoryMapper;
import com.lin.distribution.service.service.IProductCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品分类Service业务层处理
 * 
 * @author lin
 * @date 2024-11-02
 */
@Service
public class ProductCategoryServiceImpl implements IProductCategoryService 
{
    @Autowired
    private ProductCategoryMapper productCategoryMapper;

    /**
     * 查询商品分类
     * 
     * @param id 商品分类主键
     * @return 商品分类
     */
    @Override
    public ProductCategory selectProductCategoryById(Long id)
    {
        return productCategoryMapper.selectProductCategoryById(id);
    }

    /**
     * 查询商品分类列表
     * 
     * @param productCategory 商品分类
     * @return 商品分类
     */
    @Override
    public List<ProductCategory> selectProductCategoryList(ProductCategory productCategory)
    {
        productCategory.setIsDeleted(Boolean.FALSE);
        return productCategoryMapper.selectProductCategoryList(productCategory);
    }

    /**
     * 新增商品分类
     * 
     * @param productCategory 商品分类
     * @return 结果
     */
    @Override
    public int insertProductCategory(ProductCategory productCategory) {
        // check unique category
        checkUniqueCategory(productCategory);

        Long parentId = productCategory.getParentId();
        // set level
        if (parentId == null || parentId == 0L) {
            productCategory.setLevel(1);
        } else {
            ProductCategory parent = productCategoryMapper.selectProductCategoryById(parentId);
            productCategory.setLevel(parent.getLevel() + 1);
        }
        productCategory.setCode(this.generateCode(parentId));
        productCategory.setIsDeleted(Boolean.FALSE);
        productCategory.setCreateTime(DateUtils.getNowDate());
        return productCategoryMapper.insertProductCategory(productCategory);
    }

    /**
     * 修改商品分类
     * 
     * @param productCategory 商品分类
     * @return 结果
     */
    @Override
    public int updateProductCategory(ProductCategory productCategory)
    {
        // check unique category
        checkUniqueCategory(productCategory);

        productCategory.setUpdateTime(DateUtils.getNowDate());
        return productCategoryMapper.updateProductCategory(productCategory);
    }

    /**
     * 批量删除商品分类
     * 包括本身及其子分类
     * 
     * @param ids 需要删除的商品分类主键
     * @return 结果
     */
    @Override
    public int deleteProductCategoryByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }

        // 删除本身及其子类
        List<Long> removeIds = new ArrayList<>();
        for (Long id : ids) {
            List<Long> categoryIds = getCategoryAndSubCategoryIds(productCategoryMapper.selectProductCategoryList(new ProductCategory()), id);
            removeIds.addAll(categoryIds);
        }

        return productCategoryMapper.deleteProductCategoryByIds(removeIds.toArray(new Long[0]));
    }

    // 获取自身及所有子分类的 id 列表
    public static List<Long> getCategoryAndSubCategoryIds(List<ProductCategory> categories, Long categoryId) {
        List<Long> result = new ArrayList<>();
        ProductCategory category = categories.stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElse(null);

        if (category != null) {
            result.add(category.getId());
            getSubCategoryIds(categories, category.getId(), result);
        }

        return result;
    }

    // 递归方法：获取所有子分类的 id 列表
    private static void getSubCategoryIds(List<ProductCategory> categories, Long parentId, List<Long> result) {
        List<ProductCategory> subCategories = categories.stream()
                .filter(c -> c.getParentId() != null && c.getParentId().equals(parentId)).toList();

        for (ProductCategory subCategory : subCategories) {
            result.add(subCategory.getId());
            getSubCategoryIds(categories, subCategory.getId(), result);
        }
    }

    /**
     * 删除商品分类信息
     * 
     * @param id 商品分类主键
     * @return 结果
     */
    @Override
    public int deleteProductCategoryById(Long id)
    {
        return productCategoryMapper.deleteProductCategoryById(id);
    }

    @Override
    public Long getNextProductCategorySort(Long id) {
        ProductCategory p = new ProductCategory();
        // 新增大类
        if (id == 0L) {
            p.setLevel(1);
            long count = productCategoryMapper.selectProductCategoryList(p).size();
            return count + 1;
        }

        // 新增子类
        ProductCategory productCategory = productCategoryMapper.selectProductCategoryById(id);
        if (productCategory != null) {
            p.setParentId(id);
            Long count = productCategoryMapper.selectProductCategoryCount(p);
            return count + 1;
        }

        // 默认为 0
        return 0L;
    }

    @Override
    public String generateCode(Long parentId) {
        ProductCategory p = new ProductCategory();
        // 新增大类
        if (parentId == 0L) {
            p.setLevel(1);
            long count = productCategoryMapper.selectProductCategoryList(p).size();
            return String.valueOf((count + 10 + 1) * 10000);
        }

        // 新增子类
        ProductCategory parent = productCategoryMapper.selectProductCategoryById(parentId);
        if (parent != null) {
            p.setParentId(parentId);
            Long count = productCategoryMapper.selectProductCategoryCount(p);
            return String.valueOf(Integer.parseInt(parent.getCode()) + count + 1);
        }

        // 默认为 0
        return "0";
    }

    private void checkUniqueCategory(ProductCategory productCategory) {
        if (productCategory == null) {
            throw new ServiceException("product category is null");
        }

        List<ProductCategory> categories = productCategoryMapper.selectProductCategoryByName(productCategory.getName());

        long count = 0;
        if (productCategory.getId() != null) {
            count = categories.stream()
                    .filter(item -> !item.getId().equals(productCategory.getId()))
                    .count();
        } else {
            count = categories.size();
        }

        if (count > 0) {
            throw new ServiceException("category name is exist");
        }
    }
}

package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.domain.ProductAlias;
import com.lin.distribution.mapper.ProductAliasMapper;
import com.lin.distribution.service.ProductAliasService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品全局别名Service业务层处理
 *
 * @author lin
 * @date 2024-11-20
 */
@Service
public class ProductAliasServiceImpl implements ProductAliasService
{
    @Autowired
    private ProductAliasMapper productAliasMapper;

    /**
     * 查询商品全局别名
     *
     * @param id 商品全局别名主键
     * @return 商品全局别名
     */
    @Override
    public ProductAlias selectProductAliasById(Long id)
    {
        return productAliasMapper.selectProductAliasById(id);
    }

    /**
     * 查询商品全局别名列表
     *
     * @param productAlias 商品全局别名
     * @return 商品全局别名
     */
    @Override
    public List<ProductAlias> selectProductAliasList(ProductAlias productAlias)
    {
        return productAliasMapper.selectProductAliasList(productAlias);
    }

    /**
     * 按关键词检索别名
     *
     * @param keyword 关键词
     * @return 商品全局别名集合
     */
    @Override
    public List<ProductAlias> listByKeyword(String keyword)
    {
        return productAliasMapper.selectProductAliasByKeyword(keyword);
    }

    /**
     * 新增商品全局别名
     *
     * @param productAlias 商品全局别名
     * @return 结果
     */
    @Override
    public int insertProductAlias(ProductAlias productAlias)
    {
        // check unique alias
        checkUniqueAlias(productAlias);

        productAlias.setCreateTime(DateUtils.getNowDate());
        return productAliasMapper.insertProductAlias(productAlias);
    }

    /**
     * 修改商品全局别名
     *
     * @param productAlias 商品全局别名
     * @return 结果
     */
    @Override
    public int updateProductAlias(ProductAlias productAlias)
    {
        // check unique alias
        checkUniqueAlias(productAlias);

        productAlias.setUpdateTime(DateUtils.getNowDate());
        return productAliasMapper.updateProductAlias(productAlias);
    }

    /**
     * 批量删除商品全局别名
     *
     * @param ids 需要删除的商品全局别名主键
     * @return 结果
     */
    @Override
    public int deleteProductAliasByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return productAliasMapper.deleteProductAliasByIds(ids);
    }

    private void checkUniqueAlias(ProductAlias productAlias) {
        if (productAlias == null) {
            throw new ServiceException("product alias is null");
        }
        if (productAlias.getAlias() == null || productAlias.getAlias().trim().isEmpty()) {
            throw new ServiceException("别名不能为空");
        }

        List<ProductAlias> aliases = productAliasMapper.selectProductAliasByAlias(productAlias.getAlias());

        long count = 0;
        if (productAlias.getId() != null) {
            count = aliases.stream()
                    .filter(item -> !item.getId().equals(productAlias.getId()))
                    .count();
        } else {
            count = aliases.size();
        }

        if (count > 0) {
            throw new ServiceException("别名已存在");
        }
    }
}

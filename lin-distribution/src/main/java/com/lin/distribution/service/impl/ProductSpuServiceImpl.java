package com.lin.distribution.service.impl;

import java.util.List;

import com.lin.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.ProductSpuMapper;
import com.lin.distribution.domain.ProductSpu;
import com.lin.distribution.service.ProductSpuService;

/**
 * 商品spuService业务层处理
 *
 * @author lin
 * @date 2024-11-07
 */
@Service
public class ProductSpuServiceImpl implements ProductSpuService {
    @Autowired
    private ProductSpuMapper productSpuMapper;

    /**
     * 查询商品spu
     *
     * @param id 商品spu主键
     * @return 商品spu
     */
    @Override
    public ProductSpu selectProductSpuById(Long id) {
        return productSpuMapper.selectProductSpuById(id);
    }

    /**
     * 查询商品spu列表
     *
     * @param productSpu 商品spu
     * @return 商品spu
     */
    @Override
    public List<ProductSpu> selectProductSpuList(ProductSpu productSpu) {
        return productSpuMapper.selectProductSpuList(productSpu);
    }

    /**
     * 新增商品spu
     *
     * @param productSpu 商品spu
     * @return 结果
     */
    @Override
    public int insertProductSpu(ProductSpu productSpu) {
        productSpu.setCreateTime(DateUtils.getNowDate());
        return productSpuMapper.insertProductSpu(productSpu);
    }

    /**
     * 修改商品spu
     *
     * @param productSpu 商品spu
     * @return 结果
     */
    @Override
    public int updateProductSpu(ProductSpu productSpu) {
        productSpu.setUpdateTime(DateUtils.getNowDate());
        return productSpuMapper.updateProductSpu(productSpu);
    }

    /**
     * 批量删除商品spu
     *
     * @param ids 需要删除的商品spu主键
     * @return 结果
     */
    @Override
    public int deleteProductSpuByIds(Long[] ids) {
        return productSpuMapper.deleteProductSpuByIds(ids);
    }

    /**
     * 删除商品spu信息
     *
     * @param id 商品spu主键
     * @return 结果
     */
    @Override
    public int deleteProductSpuById(Long id) {
        return productSpuMapper.deleteProductSpuById(id);
    }
}
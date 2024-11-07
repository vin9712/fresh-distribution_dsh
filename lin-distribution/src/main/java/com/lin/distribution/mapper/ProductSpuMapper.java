package com.lin.distribution.mapper;

import java.util.List;
import com.lin.distribution.domain.ProductSpu;

/**
 * 商品spuMapper接口
 *
 * @author lin
 * @date 2024-11-07
 */
public interface ProductSpuMapper
{
    /**
     * 查询商品spu
     *
     * @param id 商品spu主键
     * @return 商品spu
     */
    ProductSpu selectProductSpuById(Long id);

    /**
     * 查询商品spu列表
     *
     * @param productSpu 商品spu
     * @return 商品spu集合
     */
    List<ProductSpu> selectProductSpuList(ProductSpu productSpu);

    /**
     * 新增商品spu
     *
     * @param productSpu 商品spu
     * @return 结果
     */
    int insertProductSpu(ProductSpu productSpu);

    /**
     * 修改商品spu
     *
     * @param productSpu 商品spu
     * @return 结果
     */
    int updateProductSpu(ProductSpu productSpu);

    /**
     * 删除商品spu
     *
     * @param id 商品spu主键
     * @return 结果
     */
    int deleteProductSpuById(Long id);

    /**
     * 批量删除商品spu
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteProductSpuByIds(Long[] ids);
}
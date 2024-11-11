package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.domain.ProductSku;

/**
 * 商品信息Service接口
 *
 * @author lin
 * @date 2024-11-11
 */
public interface ProductSkuService {
    /**
     * 查询商品信息
     *
     * @param id 商品信息主键
     * @return 商品信息
     */
    ProductSku selectProductSkuById(String id);

    /**
     * 查询商品信息列表
     *
     * @param productSku 商品信息
     * @return 商品信息集合
     */
    List<ProductSku> selectProductSkuList(ProductSku productSku);

    /**
     * 新增商品信息
     *
     * @param productSku 商品信息
     * @return 结果
     */
    int insertProductSku(ProductSku productSku);

    /**
     * 修改商品信息
     *
     * @param productSku 商品信息
     * @return 结果
     */
    int updateProductSku(ProductSku productSku);

    /**
     * 批量删除商品信息
     *
     * @param ids 需要删除的商品信息主键集合
     * @return 结果
     */
    int deleteProductSkuByIds(String[] ids);

    /**
     * 删除商品信息信息
     *
     * @param id 商品信息主键
     * @return 结果
     */
    int deleteProductSkuById(String id);

    /**
     * 生成客户商品编号
     *
     * @param spuId
     * @param spuCode spu 助记码
     * @param customerId
     * @param isParent
     * @return
     */
    String generateSkuNo(Long customerId, Long spuId, String spuCode, Boolean isParent);
}

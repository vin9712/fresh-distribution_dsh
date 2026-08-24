package com.lin.distribution.service;

import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.domain.ProductSpu;
import com.lin.distribution.dto.ProductSkuMatchDTO;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * @author vinga
 * @date 2024/11/13
 */
public interface ProductService {

    /** *************************** sku *************************** **/

    /**
     * 查询商品信息
     *
     * @param skuId 商品信息主键
     * @return 商品信息
     */
    ProductSku selectProductSkuById(Long skuId);

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
     * @param skuIds 需要删除的商品信息主键集合
     * @return 结果
     */
    int deleteProductSkuByIds(String[] skuIds);

    /**
     * 删除商品信息信息
     *
     * @param skuId 商品信息主键
     * @return 结果
     */
    int deleteProductSkuById(String skuId);

    /** *************************** spu *************************** **/

    /**
     * 查询商品spu
     *
     * @param spuId 商品spu主键
     * @return 商品spu
     */
    ProductSpu selectProductSpuById(Long spuId);

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
    ProductSpu insertProductSpu(ProductSpu productSpu);

    /**
     * 修改商品spu
     *
     * @param productSpu 商品spu
     * @return 结果
     */
    int updateProductSpu(ProductSpu productSpu);

    /**
     * 批量删除商品spu
     *
     * @param spuIds 需要删除的商品spu主键集合
     * @return 结果
     */
    int deleteProductSpuByIds(Long[] spuIds);

    /**
     * 删除商品spu信息
     *
     * @param spuId 商品spu主键
     * @return 结果
     */
    int deleteProductSpuById(Long spuId);

    /**
     * 导入商品
     * @param skuList
     * @return
     */
    String importProductSku(List<ProductSku> skuList);

    /**
     * 导入商品库（SPU）：同分类+名称重复跳过
     * @param spuList
     * @return
     */
    String importProductSpu(List<ProductSpu> spuList);

    /**
     * 粘贴文本快速导入商品库（每行一条：支持「分类/商品名」或直接「商品名」）
     * @param text 粘贴的文本内容
     * @param defaultCategoryId 行内未指定分类时使用的默认分类ID（可为空）
     * @return 导入结果报告
     */
    String importProductSpuText(String text, Long defaultCategoryId);

    int matchProductSku(ProductSkuMatchDTO request);

    int undoMatchProductSku(ProductSkuMatchDTO request);
}

package com.lin.distribution.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lin.distribution.domain.ProductSku;
import org.apache.ibatis.annotations.Param;

/**
 * 商品信息Mapper接口
 *
 * @author lin
 * @date 2024-11-11
 */
public interface ProductSkuMapper extends BaseMapper<ProductSku> {
    /**
     * 查询商品信息
     *
     * @param id 商品信息主键
     * @return 商品信息
     */
    ProductSku selectProductSkuById(Long id);

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
     * 批量清空匹配商品库
     * @param ids
     * @return
     */
    int undoMatchProductSku(Long[] ids);

    /**
     * 删除商品信息
     *
     * @param id 商品信息主键
     * @return 结果
     */
    int deleteProductSkuById(String id);

    /**
     * 批量删除商品信息
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteProductSkuByIds(String[] ids);

    /**
     * 根据分类id、名称、规格、单位查询标准SKU（唯一性校验）
     *
     * @param categoryId 分类id
     * @param name       商品名称
     * @param specName   规格
     * @param unit       单位
     * @return 商品信息集合
     */
    List<ProductSku> selectProductSkuByCategoryNameSpecUnit(@Param("categoryId") Long categoryId, @Param("name") String name, @Param("specName") String specName, @Param("unit") String unit);
}
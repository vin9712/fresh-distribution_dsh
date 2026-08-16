package com.lin.distribution.mapper;

import com.lin.distribution.domain.TempProduct;

import java.util.List;

/**
 * 临时商品Mapper接口
 *
 * @author lin
 * @date 2024-11-20
 */
public interface TempProductMapper {
    /**
     * 查询临时商品
     *
     * @param id 临时商品主键
     * @return 临时商品
     */
    TempProduct selectTempProductById(Long id);

    /**
     * 查询临时商品列表
     *
     * @param tempProduct 临时商品
     * @return 临时商品集合
     */
    List<TempProduct> selectTempProductList(TempProduct tempProduct);

    /**
     * 新增临时商品
     *
     * @param tempProduct 临时商品
     * @return 结果
     */
    int insertTempProduct(TempProduct tempProduct);

    /**
     * 修改临时商品
     *
     * @param tempProduct 临时商品
     * @return 结果
     */
    int updateTempProduct(TempProduct tempProduct);

    /**
     * 批量删除临时商品
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTempProductByIds(Long[] ids);
}

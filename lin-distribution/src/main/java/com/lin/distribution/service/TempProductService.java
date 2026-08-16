package com.lin.distribution.service;

import com.lin.distribution.domain.TempProduct;
import com.lin.distribution.dto.TempProductConvertDTO;

import java.util.List;

/**
 * 临时商品Service接口
 *
 * @author lin
 * @date 2024-11-20
 */
public interface TempProductService {
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
     * @param ids 需要删除的临时商品主键集合
     * @return 结果
     */
    int deleteTempProductByIds(Long[] ids);

    /**
     * 临时商品转正为正式SKU
     *
     * @param dto 转正请求（id 必填；customerId/categoryId/mnemonicCode 可空）
     * @return 新生成的 skuId
     */
    Long convertToSku(TempProductConvertDTO dto);
}

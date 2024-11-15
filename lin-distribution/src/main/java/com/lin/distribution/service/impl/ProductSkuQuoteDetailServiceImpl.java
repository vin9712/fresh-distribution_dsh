package com.lin.distribution.service.impl;

import java.util.List;

import com.lin.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.ProductSkuQuoteDetailMapper;
import com.lin.distribution.domain.ProductSkuQuoteDetail;
import com.lin.distribution.service.ProductSkuQuoteDetailService;

/**
 * 商品报价明细Service业务层处理
 *
 * @author lin
 * @date 2024-11-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSkuQuoteDetailServiceImpl implements ProductSkuQuoteDetailService {
    private final ProductSkuQuoteDetailMapper productSkuQuoteDetailMapper;

    /**
     * 查询商品报价明细
     *
     * @param id 商品报价明细主键
     * @return 商品报价明细
     */
    @Override
    public ProductSkuQuoteDetail selectProductSkuQuoteDetailById(Long id) {
        return productSkuQuoteDetailMapper.selectProductSkuQuoteDetailById(id);
    }

    /**
     * 查询商品报价明细列表
     *
     * @param productSkuQuoteDetail 商品报价明细
     * @return 商品报价明细
     */
    @Override
    public List<ProductSkuQuoteDetail> selectProductSkuQuoteDetailList(ProductSkuQuoteDetail productSkuQuoteDetail) {
        return productSkuQuoteDetailMapper.selectProductSkuQuoteDetailList(productSkuQuoteDetail);
    }

    /**
     * 新增商品报价明细
     *
     * @param productSkuQuoteDetail 商品报价明细
     * @return 结果
     */
    @Override
    public int insertProductSkuQuoteDetail(ProductSkuQuoteDetail productSkuQuoteDetail) {
        productSkuQuoteDetail.setCreateTime(DateUtils.getNowDate());
        return productSkuQuoteDetailMapper.insertProductSkuQuoteDetail(productSkuQuoteDetail);
    }

    /**
     * 修改商品报价明细
     *
     * @param productSkuQuoteDetail 商品报价明细
     * @return 结果
     */
    @Override
    public int updateProductSkuQuoteDetail(ProductSkuQuoteDetail productSkuQuoteDetail) {
        productSkuQuoteDetail.setUpdateTime(DateUtils.getNowDate());
        return productSkuQuoteDetailMapper.updateProductSkuQuoteDetail(productSkuQuoteDetail);
    }

    /**
     * 批量删除商品报价明细
     *
     * @param ids 需要删除的商品报价明细主键
     * @return 结果
     */
    @Override
    public int deleteProductSkuQuoteDetailByIds(Long[] ids) {
        return productSkuQuoteDetailMapper.deleteProductSkuQuoteDetailByIds(ids);
    }

    /**
     * 删除商品报价明细信息
     *
     * @param id 商品报价明细主键
     * @return 结果
     */
    @Override
    public int deleteProductSkuQuoteDetailById(Long id) {
        return productSkuQuoteDetailMapper.deleteProductSkuQuoteDetailById(id);
    }
}

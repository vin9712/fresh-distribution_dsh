package com.lin.distribution.service.impl;

import com.lin.common.utils.DateUtils;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.domain.ProductSkuQuote;
import com.lin.distribution.domain.ProductSkuQuoteDetail;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.mapper.ProductSkuQuoteDetailMapper;
import com.lin.distribution.mapper.ProductSkuQuoteMapper;
import com.lin.distribution.service.ProductSkuQuoteDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final ProductSkuMapper productSkuMapper;
    private final ProductSkuQuoteMapper productSkuQuoteMapper;
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

    @Override
    public List<ProductSkuQuoteDetail> customerQuoteDetailList(Long customerId, Long quoteId) {
        List<ProductSkuQuoteDetail> result = new ArrayList<>();
        List<ProductSkuQuoteDetail> skuQuoteDetailList = new ArrayList<>();

        // get customer sku list
        ProductSku q = new ProductSku();
        q.setCustomerId(customerId);
        List<ProductSku> skuList = productSkuMapper.selectProductSkuList(q);
        if (CollectionUtils.isNotEmpty(skuList)) {
            for (ProductSku sku : skuList) {
                ProductSkuQuoteDetail detail = new ProductSkuQuoteDetail();
                detail.setCustomerId(customerId);
                detail.setCategoryId(sku.getCategoryId());
                detail.setCategoryName(sku.getCategoryName());
                detail.setQuoteId(quoteId);
                detail.setSkuId(sku.getId());
                detail.setProductCode(sku.getCode());
                detail.setProductMnemonicCode(sku.getMnemonicCode());
                detail.setProductName(sku.getName());
                detail.setProductUnit(sku.getUnit());
                detail.setProductSpec(sku.getSpec());
                detail.setPrice(BigDecimal.ZERO);
                skuQuoteDetailList.add(detail);
            }
        }

        // if not exist quoteId, get customer active quote
        if (quoteId == null) {
            ProductSkuQuote activeQuote = productSkuQuoteMapper.selectCustomerActiveQuote(customerId);
            if (activeQuote == null) {
                return skuQuoteDetailList;
            }
            quoteId = activeQuote.getId();
        }

        // get sku quote detail map
        Map<Long, ProductSkuQuoteDetail> skuQuoteDetailMap = skuQuoteDetailList.stream().collect(Collectors.toMap(ProductSkuQuoteDetail::getSkuId, Function.identity(), (v1, v2) -> v1));
        // add details
        List<ProductSkuQuoteDetail> quoteDetailList = productSkuQuoteDetailMapper.selectProductSkuQuoteDetailListByQuoteId(quoteId);
        if (CollectionUtils.isNotEmpty(quoteDetailList)) {
            for (ProductSkuQuoteDetail quoteDetail : quoteDetailList) {
                Long skuId = quoteDetail.getSkuId();
                ProductSkuQuoteDetail skuQuoteDetail = skuQuoteDetailMap.get(skuId);
                if (skuQuoteDetail != null) {
                    skuQuoteDetail.setId(quoteDetail.getId());
                    skuQuoteDetail.setQuoteId(quoteDetail.getQuoteId());
                    skuQuoteDetail.setPrice(quoteDetail.getPrice());
                    result.add(skuQuoteDetail);
                } else {
                    result.add(quoteDetail);
                }
            }
        }

        return result;
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

package com.lin.distribution.service.impl;

import java.util.List;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.domain.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.ProductSkuQuoteMapper;
import com.lin.distribution.domain.ProductSkuQuote;
import com.lin.distribution.service.ProductSkuQuoteService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品报价Service业务层处理
 *
 * @author lin
 * @date 2024-11-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSkuQuoteServiceImpl implements ProductSkuQuoteService {
    private final ProductSkuQuoteMapper productSkuQuoteMapper;
    private final RedissonClient redissonClient;

    /**
     * 查询商品报价
     *
     * @param id 商品报价主键
     * @return 商品报价
     */
    @Override
    public ProductSkuQuote selectProductSkuQuoteById(Long id) {
        return productSkuQuoteMapper.selectProductSkuQuoteById(id);
    }

    /**
     * 查询商品报价列表
     *
     * @param productSkuQuote 商品报价
     * @return 商品报价
     */
    @Override
    public List<ProductSkuQuote> selectProductSkuQuoteList(ProductSkuQuote productSkuQuote) {
        return productSkuQuoteMapper.selectProductSkuQuoteList(productSkuQuote);
    }

    /**
     * 新增商品报价
     *
     * @param productSkuQuote 商品报价
     * @return 结果
     */
    @Override
    @Transactional
    public int insertProductSkuQuote(ProductSkuQuote productSkuQuote) {
        // check unique quote
        checkUniqueQuote(productSkuQuote);

        productSkuQuote.setCreateTime(DateUtils.getNowDate());
        return productSkuQuoteMapper.insertProductSkuQuote(productSkuQuote);
    }

    /**
     * 修改商品报价
     *
     * @param productSkuQuote 商品报价
     * @return 结果
     */
    @Override
    public int updateProductSkuQuote(ProductSkuQuote productSkuQuote) {
        productSkuQuote.setUpdateTime(DateUtils.getNowDate());
        return productSkuQuoteMapper.updateProductSkuQuote(productSkuQuote);
    }

    /**
     * 批量删除商品报价
     *
     * @param ids 需要删除的商品报价主键
     * @return 结果
     */
    @Override
    public int deleteProductSkuQuoteByIds(Long[] ids) {
        return productSkuQuoteMapper.deleteProductSkuQuoteByIds(ids);
    }

    /**
     * 删除商品报价信息
     *
     * @param id 商品报价主键
     * @return 结果
     */
    @Override
    public int deleteProductSkuQuoteById(Long id) {
        return productSkuQuoteMapper.deleteProductSkuQuoteById(id);
    }

    /**
     * 生成商品报价单号
     *
     * @param refresh 是否刷新下一个，默认不刷新，提交表单时才更新
     * @return
     */
    @Override
    public String generateSkuQuoteNo(Boolean refresh) {
        String date = DateUtils.dateTime();
        String prefix = "BJ" + date;
        RMap<String, Integer> rMap = redissonClient.getMap("skuQuoteNo");
        int seqNbr = BooleanUtils.isTrue(refresh) ? rMap.addAndGet(date, 1) : rMap.getOrDefault(date, 0);
        String seqNbrStr = String.format("%05d", seqNbr);
        return prefix + seqNbrStr;
    }

    private void checkUniqueQuote(ProductSkuQuote productSkuQuote) {
        if (productSkuQuote == null) {
            throw new ServiceException("productSkuQuote is null");
        }

        ProductSkuQuote skuQuote = productSkuQuoteMapper.selectProductSkuQuoteByCode(productSkuQuote.getCode());
        if (skuQuote != null) {
            throw new ServiceException("product sku no existed");
        }
    }
}

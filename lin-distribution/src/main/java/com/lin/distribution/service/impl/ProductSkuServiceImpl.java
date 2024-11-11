package com.lin.distribution.service.impl;

import java.util.List;

import com.lin.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.service.ProductSkuService;

/**
 * 商品信息Service业务层处理
 *
 * @author lin
 * @date 2024-11-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSkuServiceImpl implements ProductSkuService {
    private final ProductSkuMapper productSkuMapper;
    private final RedissonClient redissonClient;

    /**
     * 查询商品信息
     *
     * @param id 商品信息主键
     * @return 商品信息
     */
    @Override
    public ProductSku selectProductSkuById(String id) {
        return productSkuMapper.selectProductSkuById(id);
    }

    /**
     * 查询商品信息列表
     *
     * @param productSku 商品信息
     * @return 商品信息
     */
    @Override
    public List<ProductSku> selectProductSkuList(ProductSku productSku) {
        return productSkuMapper.selectProductSkuList(productSku);
    }

    /**
     * 新增商品信息
     *
     * @param productSku 商品信息
     * @return 结果
     */
    @Override
    public int insertProductSku(ProductSku productSku) {
        productSku.setCreateTime(DateUtils.getNowDate());
        return productSkuMapper.insertProductSku(productSku);
    }

    /**
     * 修改商品信息
     *
     * @param productSku 商品信息
     * @return 结果
     */
    @Override
    public int updateProductSku(ProductSku productSku) {
        productSku.setUpdateTime(DateUtils.getNowDate());
        return productSkuMapper.updateProductSku(productSku);
    }

    /**
     * 批量删除商品信息
     *
     * @param ids 需要删除的商品信息主键
     * @return 结果
     */
    @Override
    public int deleteProductSkuByIds(String[] ids) {
        return productSkuMapper.deleteProductSkuByIds(ids);
    }

    /**
     * 删除商品信息信息
     *
     * @param id 商品信息主键
     * @return 结果
     */
    @Override
    public int deleteProductSkuById(String id) {
        return productSkuMapper.deleteProductSkuById(id);
    }

    @Override
    public String generateSkuNo(Long customerId, Long spuId, String spuCode, Boolean isParent) {
        String prefix = spuCode + customerId + spuId;
        if (BooleanUtils.isTrue(isParent)) {
            return prefix + "00000";
        }
        RMap<Long, Integer> rMap = redissonClient.getMap("skuNo");
        int seqNbr = rMap.addAndGet(customerId, 1);
        String seqNbrStr = String.format("%05d", seqNbr);
        return prefix + seqNbrStr;
    }
}

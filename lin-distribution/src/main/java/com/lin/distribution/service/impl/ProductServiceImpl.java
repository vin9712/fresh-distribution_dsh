package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.ServletUtils;
import com.lin.common.utils.StringUtils;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.domain.ProductSpu;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.mapper.ProductSpuMapper;
import com.lin.distribution.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商品服务业务层处理
 *
 * @author vinga
 * @date 2024/11/13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductSkuMapper productSkuMapper;
    private final ProductSpuMapper productSpuMapper;
    private final RedissonClient redissonClient;
    private List<ProductSpu> spuList;

    /** *************************** sku *************************** **/

    /**
     * 查询商品信息
     *
     * @param id 商品信息主键
     * @return 商品信息
     */
    @Override
    public ProductSku selectProductSkuById(Long id) {
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
     * insert spu method = insert spu one + insert sku one(customerId=0)
     * insert sku method = insert sku one with customerId
     * if customerId = 0 & spuId = null, that means try insert spu method
     * if customerId = 0 & spuId != null, that is invalid request
     * if customerId != 0 & spuId = null, try insert spu method & insert sku method
     * if customerId != 0 & spuId != null, that means try insert sku method
     *
     * @param productSku 商品信息
     * @return 结果
     */
    @Override
    @Transactional
    public int insertProductSku(ProductSku productSku) {

        // check request validation
        checkSaveOrUpdateSkuRequest(productSku);

        // insert spu method = insert spu one + insert sku one(customerId=0)
        if (productSku.getSpuId() == null) {
            // add default productSpu
            ProductSpu productSpu = ProductSpu.builder()
                    .categoryId(productSku.getCategoryId())
                    .name(productSku.getName())
                    .mnemonicCode(productSku.getMnemonicCode())
                    .valid(1)
                    .saleable(1)
                    .isDeleted(Boolean.FALSE)
                    .build();
            insertProductSpu(productSpu);
        }

        // customerId = 0 & spuId = null, only insert spu method
        if (productSku.getCustomerId() == 0L) {
            return 1;
        }

        // insert sku one with customerId
        productSku.setCreateTime(DateUtils.getNowDate());
        return productSkuMapper.insertProductSku(productSku);
    }

    private void checkSaveOrUpdateSkuRequest(ProductSku productSku) {
        if (productSku == null) {
            throw new ServiceException("product sku is null");
        }

        if (productSku.getCustomerId() == null) {
            throw new ServiceException("customerId is null");
        }

        if (productSku.getSpuId() == null && productSku.getCategoryId() == null) {
            throw new ServiceException("categoryId or spuId is null");
        }

        if (productSku.getSpuId() != null && productSku.getCustomerId() == 0L) {
            throw new ServiceException("customerId is 0 but spuId is not null");
        }
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

    /** *************************** spu *************************** **/

    /**
     * 查询商品spu
     *
     * @param id 商品spu主键
     * @return 商品spu
     */
    @Override
    public ProductSpu selectProductSpuById(Long id) {
        return productSpuMapper.selectProductSpuById(id);
    }

    /**
     * 查询商品spu列表
     *
     * @param productSpu 商品spu
     * @return 商品spu
     */
    @Override
    public List<ProductSpu> selectProductSpuList(ProductSpu productSpu) {
        return productSpuMapper.selectProductSpuList(productSpu);
    }

    /**
     * 新增商品spu
     *
     * @param productSpu 商品spu
     * @return 结果
     */
    @Override
    @Transactional
    public ProductSpu insertProductSpu(ProductSpu productSpu) {
        // check unique spu
        checkUniqueSpu(productSpu);

        // add spu item
        productSpu.setCreateTime(DateUtils.getNowDate());
        productSpuMapper.insertProductSpu(productSpu);

        // add default sku with customerId = 0
        ProductSku productSku = ProductSku.builder()
                .customerId(0L)
                .spuId(productSpu.getId())
                .code(generateSkuNo(0L, productSpu.getId(), productSpu.getMnemonicCode(), true))
                .mnemonicCode(productSpu.getMnemonicCode())
                .name(productSpu.getName())
                .unit("斤")
                .salePrice(BigDecimal.ZERO)
                .valid(1)
                .saleable(1)
                .isDeleted(false)
                .build();
        productSkuMapper.insertProductSku(productSku);
        return productSpu;
    }

    /**
     * 修改商品spu
     *
     * @param productSpu 商品spu
     * @return 结果
     */
    @Override
    public int updateProductSpu(ProductSpu productSpu) {
        // check unique spu
        checkUniqueSpu(productSpu);

        productSpu.setUpdateTime(DateUtils.getNowDate());
        return productSpuMapper.updateProductSpu(productSpu);
    }

    /**
     * 批量删除商品spu
     *
     * @param ids 需要删除的商品spu主键
     * @return 结果
     */
    @Override
    public int deleteProductSpuByIds(Long[] ids) {
        // todo check sku list, if contains customerId != 0, throw error
        return productSpuMapper.deleteProductSpuByIds(ids);
    }

    /**
     * 删除商品spu信息
     *
     * @param id 商品spu主键
     * @return 结果
     */
    @Override
    public int deleteProductSpuById(Long id) {
        return productSpuMapper.deleteProductSpuById(id);
    }

    private void checkUniqueSpu(ProductSpu productSpu) {
        if (productSpu == null) {
            throw new ServiceException("product spu is null");
        }

        List<ProductSpu> spuList = productSpuMapper.selectProductSpuByCategoryIdAndName(productSpu.getCategoryId(), productSpu.getName());

        long count = 0;
        if (productSpu.getId() != null) {
            count = spuList.stream()
                    .filter(item -> !item.getId().equals(productSpu.getId()))
                    .count();
        } else {
            count = spuList.size();
        }

        if (count > 0) {
            throw new ServiceException("spu name is exist");
        }
    }
}

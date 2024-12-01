package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.bean.BeanValidators;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.ProductCategory;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.domain.ProductSpu;
import com.lin.distribution.dto.ProductSkuMatchDTO;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.ProductCategoryMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.mapper.ProductSpuMapper;
import com.lin.distribution.service.ProductService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    private final ProductCategoryMapper productCategoryMapper;
    private final CustomerMapper customerMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductSpuMapper productSpuMapper;
    private final RedissonClient redissonClient;
    protected final Validator validator;

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
     * <p>
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
        Long spuId = productSku.getSpuId();

        // customerId = 0 & spuId = null, insert spu + default sku
        if (spuId == null && productSku.getCustomerId() == 0L) {
            ProductSpu newSpu = ProductSpu.builder()
                    .categoryId(productSku.getCategoryId())
                    .name(productSku.getName())
                    .mnemonicCode(productSku.getMnemonicCode())
                    .valid(1)
                    .saleable(1)
                    .isDeleted(Boolean.FALSE)
                    .build();
            this.insertProductSpu(newSpu);
            return 1;
        }

        // generate sku code
        Customer customer = customerMapper.selectCustomerById(productSku.getCustomerId());
        String customerCode = customer == null ? "#" : StringUtils.substring(customer.getShowMnemonicCode(), 0, Math.min(customer.getShowMnemonicCode().length(), 4));
        String productCode = generateSkuNo(productSku.getCustomerId(), customerCode);

        // insert sku one with customerId
        ProductSpu productSpu = spuId == null ? null : productSpuMapper.selectProductSpuById(spuId);
        productSku.setSpuId(productSpu == null ? null : productSpu.getId());
        productSku.setCode(productCode);
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
        checkSaveOrUpdateSkuRequest(productSku);
        productSku.setUpdateTime(DateUtils.getNowDate());
        return productSkuMapper.updateProductSku(productSku);
    }

    private void checkSaveOrUpdateSkuRequest(ProductSku productSku) {
        if (productSku == null) {
            throw new ServiceException("product sku is null");
        }

        if (productSku.getCustomerId() == null) {
            throw new ServiceException("customerId is null");
        }

        if (productSku.getCategoryId() == null) {
            throw new ServiceException("categoryId is null");
        }

        if (productSku.getSpuId() != null && productSku.getCustomerId() == 0L) {
            throw new ServiceException("customerId is 0 but spuId is not null");
        }

        // check unique sku
        checkUniqueSku(productSku);
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
    public String generateSkuNo(Long customerId, String customerCode) {
        String prefix = customerCode + customerId;
        RMap<Long, Integer> rMap = redissonClient.getMap("skuNo");
        int seqNbr = rMap.addAndGet(customerId, 1);
        String seqNbrStr = String.format("%05d", seqNbr);
        return prefix + seqNbrStr;
    }

    private void checkUniqueSku(ProductSku productSku) {
        if (productSku == null) {
            throw new ServiceException("product sku is null");
        }

        List<ProductSku> skuList = productSkuMapper.selectProductSkuByCustomerIdAndCategoryIdAndName(productSku.getCustomerId(), productSku.getCategoryId(), productSku.getName());

        long count = 0;
        if (productSku.getId() != null) {
            count = skuList.stream()
                    .filter(item -> !item.getId().equals(productSku.getId()))
                    .count();
        } else {
            count = skuList.size();
        }

        if (count > 0) {
            throw new ServiceException("sku name is exist");
        }
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
                .categoryId(productSpu.getCategoryId())
                .spuId(productSpu.getId())
                .code(generateSkuNo(0L, "#"))
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

    @Override
    @Transactional
    public String importProductSku(List<ProductSku> skuList) {
        if (CollectionUtils.isEmpty(skuList)) {
            throw new ServiceException("导入商品数据不能为空！");
        }

        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();

        // get category map
        Map<String, Long> categoryMap = productCategoryMapper.selectProductCategoryList(new ProductCategory()).stream().collect(Collectors.toMap(ProductCategory::getCode, ProductCategory::getId));

        for (ProductSku sku : skuList) {
            try {
                // check sku format
                Long categoryId = StringUtils.isNotEmpty(sku.getCategoryCode()) ? categoryMap.getOrDefault(sku.getCategoryCode(), null) : null;
                if (categoryId == null) {
                    throw new ServiceException("商品分类为空");
                }
                Long customerId = skuList.get(0).getCustomerId();
                if (customerId == null) {
                    throw new ServiceException("客户编号为空");
                }

                // 验证是否存在这个客户
                ProductSku s = productSkuMapper.selectProductSkuByCustomerIdAndCategoryIdAndName(customerId, categoryId, sku.getName()).stream().findFirst().orElse(null);
                if (s == null) {
                    BeanValidators.validateWithException(validator, sku);
                    sku.setMnemonicCode(sku.getSkuMnemonicCode());
                    sku.setCategoryId(categoryId);
                    sku.setSaleable(1);
                    sku.setValid(1);
                    this.insertProductSku(sku);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、商品 " + sku.getName() + " 导入成功");
                } else {
                    failureNum++;
                    failureMsg.append("<br/>" + failureNum + "、商品 " + sku.getName() + " 已存在");
                }
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "、商品 " + sku.getName() + " 导入失败：";
                failureMsg.append(msg).append(e.getMessage());
                log.error(msg, e);
            }
        }
        if (failureNum > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new ServiceException(failureMsg.toString());
        } else {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }

    @Override
    public int matchProductSku(ProductSkuMatchDTO request) {
        List<ProductSku> skuList = request.getSkuList();
        if (CollectionUtils.isEmpty(skuList)) {
            throw new ServiceException("match sku list is empty！");
        }
        skuList.forEach(productSkuMapper::updateProductSku);
        return 1;
    }

    @Override
    public int undoMatchProductSku(ProductSkuMatchDTO request) {
        List<Long> skuList = CollectionUtils.isEmpty(request.getSkuList())
                ? new ArrayList<>()
                : request.getSkuList().stream().map(ProductSku::getId).filter(Objects::nonNull).toList();
        if (CollectionUtils.isEmpty(skuList)) {
            throw new ServiceException("undo match sku list is empty！");
        }
        productSkuMapper.undoMatchProductSku(skuList.toArray(Long[]::new));
        return 1;
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

package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.bean.BeanValidators;
import com.lin.distribution.domain.ProductCategory;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.domain.ProductSpu;
import com.lin.distribution.dto.ProductSkuMatchDTO;
import com.lin.distribution.mapper.ProductCategoryMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.mapper.ProductSpuMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.ProductService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ProductSkuMapper productSkuMapper;
    private final ProductSpuMapper productSpuMapper;
    private final BizCodeService bizCodeService;
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
     * 新增商品信息（标准SKU，客户无关）
     * 编码：全局唯一 S + 8位数字（biz_code_seq.sku_code）
     *
     * @param productSku 商品信息
     * @return 结果
     */
    @Override
    @Transactional
    public int insertProductSku(ProductSku productSku) {
        // check request validation
        checkSaveOrUpdateSkuRequest(productSku);

        // 生成全局唯一编码
        productSku.setCode(bizCodeService.nextSkuCode());
        if (StringUtils.isEmpty(productSku.getMnemonicCode())) {
            productSku.setMnemonicCode(productSku.getSkuMnemonicCode());
        }
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

        if (productSku.getCategoryId() == null) {
            throw new ServiceException("categoryId is null");
        }

        if (StringUtils.isEmpty(productSku.getName())) {
            throw new ServiceException("商品名称为空");
        }

        if (StringUtils.isEmpty(productSku.getUnit())) {
            throw new ServiceException("商品单位为空");
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

    private void checkUniqueSku(ProductSku productSku) {
        if (productSku == null) {
            throw new ServiceException("product sku is null");
        }

        List<ProductSku> skuList = productSkuMapper.selectProductSkuByCategoryNameSpecUnit(productSku.getCategoryId(), productSku.getName(), productSku.getSpecName(), productSku.getUnit());

        long count = 0;
        if (productSku.getId() != null) {
            count = skuList.stream()
                    .filter(item -> !item.getId().equals(productSku.getId()))
                    .count();
        } else {
            count = skuList.size();
        }

        if (count > 0) {
            throw new ServiceException("同分类下商品名称+规格+单位已存在");
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
     * 新增商品spu（不再自动创建默认SKU，SKU 由标准SKU管理独立维护）
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
        // todo check sku list（关联标准SKU时禁止删除）
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

                // 标准SKU查重：同分类+名称+规格+单位
                ProductSku s = productSkuMapper.selectProductSkuByCategoryNameSpecUnit(categoryId, sku.getName(), sku.getSpecName(), sku.getUnit()).stream().findFirst().orElse(null);
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
    @Transactional
    public String importProductSpu(List<ProductSpu> spuList) {
        if (CollectionUtils.isEmpty(spuList)) {
            throw new ServiceException("导入商品数据不能为空！");
        }

        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();

        for (ProductSpu spu : spuList) {
            try {
                if (StringUtils.isEmpty(spu.getName())) {
                    throw new ServiceException("商品名称为空");
                }
                if (spu.getCategoryId() == null) {
                    throw new ServiceException("分类ID为空");
                }
                // 同分类+名称查重
                List<ProductSpu> existList = productSpuMapper.selectProductSpuByCategoryIdAndName(spu.getCategoryId(), spu.getName());
                if (CollectionUtils.isNotEmpty(existList)) {
                    failureNum++;
                    failureMsg.append("<br/>").append(failureNum).append("、商品 ").append(spu.getName()).append(" 已存在");
                    continue;
                }
                if (spu.getSaleable() == null) {
                    spu.setSaleable(1);
                }
                if (spu.getValid() == null) {
                    spu.setValid(1);
                }
                if (spu.getSort() == null) {
                    spu.setSort(0);
                }
                spu.setIsDeleted(false);
                productSpuMapper.insertProductSpu(spu);
                successNum++;
                successMsg.append("<br/>").append(successNum).append("、商品 ").append(spu.getName()).append(" 导入成功");
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "、商品 " + spu.getName() + " 导入失败：";
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

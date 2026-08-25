package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.PinYinConvertUtils;
import com.lin.distribution.domain.CustomerSku;
import com.lin.distribution.domain.CustomerSkuMapping;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.domain.TempProduct;
import com.lin.distribution.dto.ProductCreationDTO;
import com.lin.distribution.dto.TempProductConvertDTO;
import com.lin.distribution.mapper.CustomerSkuMapper;
import com.lin.distribution.mapper.CustomerSkuMappingMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.mapper.TempProductMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.ProductCreationService;
import com.lin.distribution.service.TempProductService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * 临时商品Service业务层处理
 *
 * @author lin
 * @date 2024-11-20
 */
@Service
public class TempProductServiceImpl implements TempProductService
{
    @Autowired
    private TempProductMapper tempProductMapper;

    @Autowired
    private ProductSkuMapper productSkuMapper;

    @Autowired
    private CustomerSkuMapper customerSkuMapper;

    @Autowired
    private BizCodeService bizCodeService;

    @Autowired
    private CustomerSkuMappingMapper customerSkuMappingMapper;

    @Autowired
    private ProductCreationService productCreationService;

    /**
     * 查询临时商品
     *
     * @param id 临时商品主键
     * @return 临时商品
     */
    @Override
    public TempProduct selectTempProductById(Long id)
    {
        return tempProductMapper.selectTempProductById(id);
    }

    /**
     * 查询临时商品列表
     *
     * @param tempProduct 临时商品
     * @return 临时商品
     */
    @Override
    public List<TempProduct> selectTempProductList(TempProduct tempProduct)
    {
        return tempProductMapper.selectTempProductList(tempProduct);
    }

    /**
     * 新增临时商品
     *
     * @param tempProduct 临时商品
     * @return 结果
     */
    @Override
    public int insertTempProduct(TempProduct tempProduct)
    {
        tempProduct.setCreateTime(DateUtils.getNowDate());
        return tempProductMapper.insertTempProduct(tempProduct);
    }

    /**
     * 修改临时商品
     *
     * @param tempProduct 临时商品
     * @return 结果
     */
    @Override
    public int updateTempProduct(TempProduct tempProduct)
    {
        tempProduct.setUpdateTime(DateUtils.getNowDate());
        return tempProductMapper.updateTempProduct(tempProduct);
    }

    /**
     * 批量删除临时商品
     *
     * @param ids 需要删除的临时商品主键
     * @return 结果
     */
    @Override
    public int deleteTempProductByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return tempProductMapper.deleteTempProductByIds(ids);
    }

    /**
     * 临时商品转正为正式SKU（deepseek_redesign.md §5.4）：
     * 1. 同分类+名称+规格+单位查重，存在则复用标准SKU，否则创建标准SKU（全局编码）；
     * 2. 创建 customers_sku 关联当前客户（唯一冲突则跳过）；
     * 3. 回写 temp_product.converted_sku_id 标记已转正。
     *
     * @param dto 转正请求（id、customerId、categoryId 必填）
     * @return 新生成/复用的 skuId
     */
    @Override
    @Transactional
    public Long convertToSku(TempProductConvertDTO dto)
    {
        if (dto == null || dto.getId() == null) {
            throw new ServiceException("临时商品id不能为空");
        }

        TempProduct tempProduct = tempProductMapper.selectTempProductById(dto.getId());
        if (tempProduct == null) {
            throw new ServiceException("临时商品不存在");
        }
        if (tempProduct.getConvertedSkuId() != null) {
            throw new ServiceException("该临时商品已转正，SKU ID: " + tempProduct.getConvertedSkuId());
        }

        Long customerId = dto.getCustomerId();
        if (customerId == null) {
            throw new ServiceException("客户id不能为空");
        }
        Long categoryId = dto.getCategoryId();
        if (categoryId == null) {
            throw new ServiceException("商品分类id不能为空");
        }

        String unit = StringUtils.isNotBlank(tempProduct.getUnit()) ? tempProduct.getUnit() : "斤";
        String specName = StringUtils.isNotBlank(tempProduct.getSpec()) ? tempProduct.getSpec() : null;

        // 1. 查重：同分类+名称+规格+单位
        List<ProductSku> existList = productSkuMapper.selectProductSkuByCategoryNameSpecUnit(categoryId, tempProduct.getName(), specName, unit);
        ProductSku sku = existList.stream().findFirst().orElse(null);
        if (sku == null) {
            // 助记码：商品名称拼音首字母
            String mnemonicCode = StringUtils.isNotBlank(dto.getMnemonicCode())
                    ? dto.getMnemonicCode()
                    : PinYinConvertUtils.toFirstChar(tempProduct.getName()).toUpperCase(Locale.ROOT);
            if (StringUtils.isBlank(mnemonicCode)) {
                mnemonicCode = "P" + tempProduct.getId();
            }

            sku = ProductSku.builder()
                    .categoryId(categoryId)
                    .name(tempProduct.getName())
                    .specName(specName)
                    .unit(unit)
                    .code(bizCodeService.nextSkuCode())
                    .mnemonicCode(mnemonicCode)
                    .salePrice(tempProduct.getDefaultPrice() == null ? BigDecimal.ZERO : tempProduct.getDefaultPrice())
                    .saleable(1)
                    .valid(1)
                    .isDeleted(Boolean.FALSE)
                    .build();
            sku.setCreateTime(DateUtils.getNowDate());
            productSkuMapper.insertProductSku(sku);
        }
        Long skuId = sku.getId();

        // 2. 创建 customers_sku（唯一冲突则跳过）
        CustomerSku existCs = customerSkuMapper.selectByCustomerAndSku(customerId, skuId);
        if (existCs == null) {
            CustomerSku customerSku = new CustomerSku();
            customerSku.setCustomerId(customerId);
            customerSku.setSkuId(skuId);
            customerSku.setCustomerCode(bizCodeService.nextCustomerSkuCode(customerId));
            customerSku.setUnit(unit);
            customerSku.setMinOrderQty(BigDecimal.ONE);
            customerSku.setOrderStep(BigDecimal.ONE);
            customerSku.setIsFollowDefault(0);
            customerSku.setStatus(1);
            customerSkuMapper.insertCustomerSku(customerSku);
        }

        // 3. 补写客户别名与客户映射（转正后下次订单/导入可直接四级匹配命中，不再重复产生临时商品）
        String aliasName = tempProduct.getName();
        if (StringUtils.isNotBlank(aliasName)) {
            // 3a. customer_sku_mapping upsert（同别名已指向其他SKU时跳过不覆盖）
            CustomerSkuMapping mq = new CustomerSkuMapping();
            mq.setCustomerId(customerId);
            CustomerSkuMapping existMapping = customerSkuMappingMapper.selectCustomerSkuMappingList(mq).stream()
                    .filter(m -> aliasName.equals(m.getCustomerAlias())).findFirst().orElse(null);
            if (existMapping == null) {
                CustomerSkuMapping mapping = new CustomerSkuMapping();
                mapping.setCustomerId(customerId);
                mapping.setCustomerAlias(aliasName);
                mapping.setSkuId(skuId);
                customerSkuMappingMapper.insertCustomerSkuMapping(mapping);
            }
            // 3b. customers_sku.alias 补写（历史条目缺别名）
            CustomerSku csForAlias = customerSkuMapper.selectByCustomerAndSku(customerId, skuId);
            if (csForAlias != null && StringUtils.isBlank(csForAlias.getAlias())) {
                CustomerSku updCs = new CustomerSku();
                updCs.setId(csForAlias.getId());
                updCs.setAlias(aliasName);
                customerSkuMapper.updateCustomerSku(updCs);
            }
        }

        // 4. 回写转正标记
        TempProduct update = new TempProduct();
        update.setId(tempProduct.getId());
        update.setConvertedSkuId(skuId);
        update.setRemark("已转正式SKU:" + skuId);
        update.setUpdateTime(DateUtils.getNowDate());
        tempProductMapper.updateTempProduct(update);

        return skuId;
    }
}

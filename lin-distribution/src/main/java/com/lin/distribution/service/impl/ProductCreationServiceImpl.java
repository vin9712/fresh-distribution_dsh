package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.PinYinConvertUtils;
import com.lin.common.utils.StringUtils;
import com.lin.distribution.domain.CustomerSku;
import com.lin.distribution.domain.CustomerSkuMapping;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.domain.ProductSpu;
import com.lin.distribution.domain.TempProduct;
import com.lin.distribution.dto.ProductCreationDTO;
import com.lin.distribution.mapper.CustomerSkuMapper;
import com.lin.distribution.mapper.CustomerSkuMappingMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.mapper.ProductSpuMapper;
import com.lin.distribution.mapper.TempProductMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.ProductCreationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * 批量建品服务实现：SPU→SKU→客户商品→客户映射 幂等建链
 *
 * 设计要点（docs/01-design/IMPORT-BATCH-CREATE-DESIGN.md）：
 *  1. SKU 查重键：分类+名称+规格+单位，命中即复用；
 *  2. SPU 归并：分类+标准名，命中复用并回填 sku.spuId；未命中新建（尽力而为，存量无主SKU不受影响）；
 *  3. customers_sku upsert：存在(customerId+skuId)则跳过；
 *  4. customer_sku_mapping upsert：同别名已指向其他 SKU 时跳过不覆盖（保守），由调用方报告提示；
 *  5. 同客户同名未转正临时商品回写 converted_sku_id。
 *
 * @author dsh
 */
@Service
@RequiredArgsConstructor
public class ProductCreationServiceImpl implements ProductCreationService {

    private final ProductSpuMapper productSpuMapper;
    private final ProductSkuMapper productSkuMapper;
    private final CustomerSkuMapper customerSkuMapper;
    private final CustomerSkuMappingMapper customerSkuMappingMapper;
    private final TempProductMapper tempProductMapper;
    private final BizCodeService bizCodeService;

    @Override
    @Transactional
    public Long createOrReuse(ProductCreationDTO dto) {
        return createOrReuseWithResult(dto).skuId;
    }

    @Transactional
    @Override
    public ProductCreationService.CreationResult createOrReuseWithResult(ProductCreationDTO dto) {
        // ---------- 校验与预处理 ----------
        if (dto == null || dto.getCustomerId() == null) {
            throw new ServiceException("客户id不能为空");
        }
        if (StringUtils.isBlank(dto.getStandardName())) {
            throw new ServiceException("标准商品名不能为空");
        }
        if (dto.getCategoryId() == null || dto.getCategoryId() <= 0) {
            throw new ServiceException("商品分类不能为空");
        }

        String standardName = dto.getStandardName().trim();
        String alias = StringUtils.isNotBlank(dto.getAlias()) ? dto.getAlias().trim() : standardName;
        String unit = StringUtils.isNotBlank(dto.getUnit()) ? dto.getUnit().trim() : "斤";
        String spec = StringUtils.isNotBlank(dto.getSpec()) ? dto.getSpec().trim() : null;

        CreationResult result = new ProductCreationService.CreationResult();

        // ---------- 1. SKU 查重（分类+名称+规格+单位） ----------
        List<ProductSku> existSkus = productSkuMapper.selectProductSkuByCategoryNameSpecUnit(
                dto.getCategoryId(), standardName, spec, unit);
        ProductSku sku = existSkus.stream().findFirst().orElse(null);

        // ---------- 2. SPU 归并（新建SKU时才需要） ----------
        ProductSpu spu = null;
        if (sku == null) {
            List<ProductSpu> spus = productSpuMapper.selectProductSpuByCategoryIdAndName(
                    dto.getCategoryId(), standardName);
            spu = spus.stream().findFirst().orElse(null);
            if (spu == null) {
                spu = buildSpu(dto.getCategoryId(), standardName);
                productSpuMapper.insertProductSpu(spu);
                result.spuCreated = true;
            }

            sku = buildSku(dto.getCategoryId(), spu.getId(), standardName, spec, unit, dto.getPrice());
            productSkuMapper.insertProductSku(sku);
            result.skuCreated = true;
        } else if (sku.getSpuId() == null) {
            // 存量无主 SKU 尽力回填 SPU
            List<ProductSpu> spus = productSpuMapper.selectProductSpuByCategoryIdAndName(
                    dto.getCategoryId(), standardName);
            if (!spus.isEmpty()) {
                ProductSku upd = new ProductSku();
                upd.setId(sku.getId());
                upd.setSpuId(spus.get(0).getId());
                productSkuMapper.updateProductSku(upd);
                sku.setSpuId(spus.get(0).getId());
            }
        }
        result.skuId = sku.getId();

        // ---------- 3. customers_sku upsert ----------
        CustomerSku existCs = customerSkuMapper.selectByCustomerAndSku(dto.getCustomerId(), sku.getId());
        if (existCs == null) {
            CustomerSku cs = new CustomerSku();
            cs.setCustomerId(dto.getCustomerId());
            cs.setSkuId(sku.getId());
            cs.setAlias(alias);
            cs.setCustomerCode(bizCodeService.nextCustomerSkuCode(dto.getCustomerId()));
            cs.setUnit(unit);
            cs.setMinOrderQty(BigDecimal.ONE);
            cs.setOrderStep(BigDecimal.ONE);
            cs.setIsFollowDefault(0);
            cs.setStatus(1);
            customerSkuMapper.insertCustomerSku(cs);
            result.customerSkuCreated = true;
        } else if (StringUtils.isBlank(existCs.getAlias())) {
            // 已有池条目但缺别名 → 补写
            CustomerSku upd = new CustomerSku();
            upd.setId(existCs.getId());
            upd.setAlias(alias);
            customerSkuMapper.updateCustomerSku(upd);
        }

        // ---------- 4. customer_sku_mapping upsert（冲突保守跳过） ----------
        CustomerSkuMapping mq = new CustomerSkuMapping();
        mq.setCustomerId(dto.getCustomerId());
        boolean mappingExists = false;
        for (CustomerSkuMapping m : customerSkuMappingMapper.selectCustomerSkuMappingList(mq)) {
            if (alias.equals(m.getCustomerAlias())) {
                if (sku.getId().equals(m.getSkuId())) {
                    mappingExists = true; // 已指向本 SKU，幂等
                } else {
                    result.mappingSkippedConflict = true; // 指向其他 SKU，不覆盖
                    mappingExists = true;
                }
                break;
            }
        }
        if (!mappingExists) {
            CustomerSkuMapping mapping = new CustomerSkuMapping();
            mapping.setCustomerId(dto.getCustomerId());
            mapping.setCustomerAlias(alias);
            mapping.setSkuId(sku.getId());
            customerSkuMappingMapper.insertCustomerSkuMapping(mapping);
            result.mappingCreated = true;
        }

        // ---------- 5. 回写临时商品转正标记 ----------
        TempProduct tq = new TempProduct();
        tq.setCustomerId(dto.getCustomerId());
        tq.setName(standardName);
        tq.setShowAll(false); // 仅查未转正
        for (TempProduct tp : tempProductMapper.selectTempProductList(tq)) {
            if (standardName.equals(tp.getName())) {
                TempProduct upd = new TempProduct();
                upd.setId(tp.getId());
                upd.setConvertedSkuId(sku.getId());
                upd.setRemark("批量建品转正 SKU:" + sku.getId());
                upd.setUpdateTime(DateUtils.getNowDate());
                tempProductMapper.updateTempProduct(upd);
                result.tempConverted = true;
                break;
            }
            // 客户叫法与临时名不同时也尝试按 alias 匹配
            if (alias.equals(tp.getName())) {
                TempProduct upd = new TempProduct();
                upd.setId(tp.getId());
                upd.setConvertedSkuId(sku.getId());
                upd.setRemark("批量建品转正 SKU:" + sku.getId());
                upd.setUpdateTime(DateUtils.getNowDate());
                tempProductMapper.updateTempProduct(upd);
                result.tempConverted = true;
                break;
            }
        }

        return result;
    }

    private ProductSpu buildSpu(Long categoryId, String name) {
        ProductSpu spu = new ProductSpu();
        spu.setCategoryId(categoryId);
        spu.setName(name);
        spu.setMnemonicCode(autoMnemonic(name));
        spu.setSaleable(1);
        spu.setSort(0);
        spu.setValid(1);
        spu.setIsDeleted(Boolean.FALSE);
        spu.setCreateTime(DateUtils.getNowDate());
        return spu;
    }

    private ProductSku buildSku(Long categoryId, Long spuId, String name, String specName, String unit, BigDecimal price) {
        ProductSku sku = ProductSku.builder()
                .categoryId(categoryId)
                .spuId(spuId)
                .name(name)
                .specName(specName)
                .unit(unit)
                .code(bizCodeService.nextSkuCode())
                .mnemonicCode(autoMnemonic(name))
                .salePrice(price == null ? BigDecimal.ZERO : price)
                .saleable(1)
                .valid(1)
                .isDeleted(Boolean.FALSE)
                .build();
        sku.setCreateTime(DateUtils.getNowDate());
        return sku;
    }

    /** 助记码自动生成：中文名首字母大写；失败退化为 P+时间戳 保证非空 */
    private String autoMnemonic(String name) {
        String code = PinYinConvertUtils.toFirstChar(name);
        if (StringUtils.isBlank(code)) {
            code = "P" + System.currentTimeMillis();
        }
        return code.toUpperCase(Locale.ROOT);
    }
}

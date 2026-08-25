package com.lin.distribution.service;

import com.lin.distribution.dto.ProductCreationDTO;

/**
 * 批量建品服务：SPU→SKU→客户商品→客户映射 幂等建链
 *
 * 设计见 docs/01-design/IMPORT-BATCH-CREATE-DESIGN.md
 *
 * @author dsh
 */
public interface ProductCreationService {

    /** 建链结果上下文：skuId + 各环节动作（新建/复用/跳过），供调用方生成导入报告 */
    class CreationResult {
        public Long skuId;
        public boolean spuCreated;
        public boolean skuCreated;
        public boolean customerSkuCreated;
        public boolean mappingCreated;
        public boolean mappingSkippedConflict;
        public boolean tempConverted;
    }

    /**
     * 建/复用 SPU→SKU→customers_sku→customer_sku_mapping 全链路（幂等）。
     * 同时回写同客户同名未转正临时商品的 converted_sku_id。
     *
     * @param dto 建品入参
     * @return skuId（新建或复用）
     */
    Long createOrReuse(ProductCreationDTO dto);

    /**
     * 同 createOrReuse，但返回详细结果供导入报告统计。
     */
    CreationResult createOrReuseWithResult(ProductCreationDTO dto);
}

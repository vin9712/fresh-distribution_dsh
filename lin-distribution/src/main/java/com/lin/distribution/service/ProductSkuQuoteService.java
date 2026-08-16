package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.constant.ProductSkuQuoteStatus;
import com.lin.distribution.domain.ProductSkuQuote;
import com.lin.distribution.dto.ProductSkuQuoteCreateDTO;
import com.lin.distribution.dto.ProductSkuQuoteImportDTO;
import com.lin.distribution.dto.ProductSkuQuoteUpdateStatusDTO;

/**
 * 商品报价Service接口
 *
 * @author lin
 * @date 2024-11-14
 */
public interface ProductSkuQuoteService {
    /**
     * 查询商品报价
     *
     * @param id 商品报价主键
     * @return 商品报价
     */
    ProductSkuQuote selectProductSkuQuoteById(Long id);

    /**
     * 查询商品报价列表
     *
     * @param productSkuQuote 商品报价
     * @return 商品报价集合
     */
    List<ProductSkuQuote> selectProductSkuQuoteList(ProductSkuQuote productSkuQuote);

    /**
     * 新增商品报价
     *
     * @param productSkuQuote 商品报价
     * @return 结果
     */
    int insertProductSkuQuote(ProductSkuQuote productSkuQuote);

    /**
     * 修改商品报价
     *
     * @param productSkuQuote 商品报价
     * @return 结果
     */
    int updateProductSkuQuote(ProductSkuQuote productSkuQuote);

    /**
     * 批量删除商品报价
     *
     * @param ids 需要删除的商品报价主键集合
     * @return 结果
     */
    int deleteProductSkuQuoteByIds(Long[] ids);

    /**
     * 删除商品报价信息
     *
     * @param id 商品报价主键
     * @return 结果
     */
    int deleteProductSkuQuoteById(Long id);

    /**
     * 生成商品报价单号
     * @return
     */
    String generateSkuQuoteNo(Boolean refresh, String currentCode);

    /**
     * 创建商品报价单含详情
     * @param request
     * @return
     */
    ProductSkuQuote createSkuQuote(ProductSkuQuoteCreateDTO request);

    /**
     * 更新商品报价单含详情
     * @param request
     * @return
     */
    ProductSkuQuote updateSkuQuote(ProductSkuQuoteCreateDTO request);

    /**
     * 更新报价单状态，同时根据当前时间启用报价
     */
    void updateQuoteStatus(ProductSkuQuoteUpdateStatusDTO request);

    /**
     * 定时任务，批量更新客户报价状态
     */
    void syncUpdateQuoteStatus();

    /**
     * 获取客户当前有效的报价单
     * @param customerId
     * @return
     */
    ProductSkuQuote getCustomerActiveQuote(Long customerId);

    /**
     * 导入客户报价：多客户多行，按客户聚合生成报价单（同客户+SKU 已存在则新建，不覆盖）
     * @param rows 导入行
     * @return 结果消息
     */
    String importQuoteData(List<ProductSkuQuoteImportDTO> rows);
}

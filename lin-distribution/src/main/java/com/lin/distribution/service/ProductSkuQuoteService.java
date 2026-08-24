package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.constant.ProductSkuQuoteStatus;
import com.lin.distribution.domain.ProductSkuQuote;
import com.lin.distribution.dto.ProductSkuQuoteCreateDTO;
import com.lin.distribution.dto.ProductSkuQuoteImportDTO;
import com.lin.distribution.dto.ProductSkuQuoteUpdateStatusDTO;
import com.lin.distribution.dto.QuotePriceImportConfirmDTO;
import com.lin.distribution.dto.QuotePriceImportDTO;

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
     * 粘贴价格表导入预览：解析文本并自动匹配内部 SKU
     * 匹配顺序：名称精确 → 助记码 → 全局别名 → 该客户的SKU映射
     *
     * @param customerId 客户ID
     * @param text       价格表文本（每行：商品叫法 价格）
     * @return 预览行列表
     */
    List<QuotePriceImportDTO.Row> previewQuotePriceImport(Long customerId, String text);

    /**
     * 价格表Excel导入预览：EasyExcel无模型流式读取，逐行解析后自动匹配内部 SKU
     *
     * @param customerId 客户ID
     * @param in         Excel输入流（.xlsx/.xls）
     * @return 预览行列表
     */
    List<QuotePriceImportDTO.Row> previewQuotePriceImportExcel(Long customerId, java.io.InputStream in);

    /**
     * 按预览确认结果导入：已匹配行生成报价单草稿，未匹配行可转临时商品
     *
     * @param dto 确认结果
     * @return 导入结果报告
     */
    String confirmQuotePriceImport(QuotePriceImportConfirmDTO dto);

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

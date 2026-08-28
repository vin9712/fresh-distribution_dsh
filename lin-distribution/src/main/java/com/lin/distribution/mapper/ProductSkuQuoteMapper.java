package com.lin.distribution.mapper;

import java.time.LocalDate;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lin.distribution.domain.ProductSkuQuote;
import org.apache.ibatis.annotations.Param;

/**
 * 商品报价Mapper接口
 *
 * @author lin
 * @date 2024-11-14
 */
public interface ProductSkuQuoteMapper {
    /**
     * 查询商品报价
     *
     * @param id 商品报价主键
     * @return 商品报价
     */
    ProductSkuQuote selectProductSkuQuoteById(Long id);

    /**
     * 根据code查询商品报价
     * @param code
     * @return
     */
    ProductSkuQuote selectProductSkuQuoteByCode(String code);

    /**
     * 查询商品报价列表
     *
     * @param productSkuQuote 商品报价
     * @return 商品报价集合
     */
    List<ProductSkuQuote> selectProductSkuQuoteList(ProductSkuQuote productSkuQuote);

    /**
     * 查询客户有效的最新报价
     * @param customerId
     * @return
     */
    ProductSkuQuote selectCustomerActiveQuote(Long customerId);

    /**
     * 查询客户最新的报价(effectiveEndDate 最新, 已发布未启用)
     * @param customerId
     * @return
     */
    ProductSkuQuote selectCustomerLatestQuote(Long customerId);

    /**
     * 查询同客户下与给定有效期区间重叠的已发布报价（不含指定排除单）
     * 用于发布时校验「同客户、同商品已发布报价有效期不得重叠」（蓝图 W0-1/报价冲突）
     *
     * @param customerId 客户ID
     * @param excludeQuoteId 排除的报价单ID（当前发布单）
     * @param startDate 待发布报价生效开始日期
     * @param endDate 待发布报价生效结束日期
     * @return 重叠的已发布报价列表
     */
    List<ProductSkuQuote> selectOverlappingPublishedQuotes(@Param("customerId") Long customerId,
                                                           @Param("excludeQuoteId") Long excludeQuoteId,
                                                           @Param("startDate") LocalDate startDate,
                                                           @Param("endDate") LocalDate endDate);

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
     * 删除商品报价
     *
     * @param id 商品报价主键
     * @return 结果
     */
    int deleteProductSkuQuoteById(Long id);

    /**
     * 批量删除商品报价
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteProductSkuQuoteByIds(Long[] ids);
}

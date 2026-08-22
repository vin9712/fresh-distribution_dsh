package com.lin.distribution.mapper;

import com.lin.distribution.dto.GlobalSearchVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 全局搜索 Mapper（客户 / 商品 / 订单 三组命中）
 *
 * @author dsh
 */
public interface GlobalSearchMapper {

    /**
     * 按关键词命中客户（名称 / 别名 / 电话 / 地址）
     *
     * @param keyword 关键词
     * @param limit   每组封顶条数
     * @return 客户命中集合
     */
    List<GlobalSearchVO.CustomerHit> selectCustomerHits(@Param("keyword") String keyword,
                                                        @Param("limit") int limit);

    /**
     * 按关键词命中商品 SKU（名称 / 编码 / 助记码 / 别名）
     *
     * @param keyword 关键词
     * @param limit   每组封顶条数
     * @return 商品命中集合
     */
    List<GlobalSearchVO.ProductHit> selectProductHits(@Param("keyword") String keyword,
                                                      @Param("limit") int limit);

    /**
     * 按关键词命中销售订单（单号 / 客户名称 / 客户别名）
     *
     * @param keyword 关键词
     * @param limit   每组封顶条数
     * @return 订单命中集合
     */
    List<GlobalSearchVO.OrderHit> selectOrderHits(@Param("keyword") String keyword,
                                                  @Param("limit") int limit);
}

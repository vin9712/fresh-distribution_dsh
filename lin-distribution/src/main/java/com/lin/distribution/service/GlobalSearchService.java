package com.lin.distribution.service;

import com.lin.distribution.dto.GlobalSearchVO;

/**
 * 全局搜索 Ctrl+K 服务
 *
 * @author dsh
 */
public interface GlobalSearchService {

    /**
     * 全局搜索（客户 / 商品 / 订单 分组）
     *
     * @param keyword 关键词
     * @param limit   每组封顶条数（缺省 10，封顶 30）
     * @return 分组命中结果
     */
    GlobalSearchVO search(String keyword, Integer limit);
}

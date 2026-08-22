package com.lin.distribution.service.impl;

import com.lin.distribution.dto.GlobalSearchVO;
import com.lin.distribution.mapper.GlobalSearchMapper;
import com.lin.distribution.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 全局搜索 Ctrl+K 服务实现
 * 规则：keyword 为空返回空分组；limit 缺省 10、封顶 30。
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalSearchServiceImpl implements GlobalSearchService {
    private final GlobalSearchMapper globalSearchMapper;

    @Override
    public GlobalSearchVO search(String keyword, Integer limit) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return GlobalSearchVO.builder()
                    .keyword("")
                    .customers(Collections.emptyList())
                    .products(Collections.emptyList())
                    .orders(Collections.emptyList())
                    .build();
        }
        int lim = (limit == null || limit <= 0) ? 10 : Math.min(limit, 30);
        return GlobalSearchVO.builder()
                .keyword(kw)
                .customers(globalSearchMapper.selectCustomerHits(kw, lim))
                .products(globalSearchMapper.selectProductHits(kw, lim))
                .orders(globalSearchMapper.selectOrderHits(kw, lim))
                .build();
    }
}

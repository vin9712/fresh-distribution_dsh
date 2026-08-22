package com.lin.distribution.controller;

import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.distribution.service.GlobalSearchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局搜索 Controller（Ctrl+K）
 *
 * @author dsh
 */
@Tag(name = "全局搜索接口")
@RestController
@RequestMapping("/search")
public class GlobalSearchController extends BaseController {
    @Autowired
    private GlobalSearchService globalSearchService;

    /**
     * 全局搜索：客户 / 商品 / 订单 分组命中
     *
     * @param keyword 关键词
     * @param limit   每组封顶条数（缺省 10，封顶 30）
     * @return 分组命中结果
     */
    @GetMapping("/global")
    public AjaxResult global(@RequestParam(value = "keyword") String keyword,
                             @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit) {
        return success(globalSearchService.search(keyword, limit));
    }
}

package com.lin.distribution.vo;

import com.lin.distribution.domain.ProductSpu;
import lombok.Data;

/**
 * @author vinga
 * @date 2024/11/12
 */
@Data
public class ProductSpuVo extends ProductSpu {
    /**
     * 分类名称
     */
    private String categoryName;
}

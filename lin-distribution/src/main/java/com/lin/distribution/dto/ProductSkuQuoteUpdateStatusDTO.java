package com.lin.distribution.dto;

import com.lin.distribution.constant.ProductSkuQuoteStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author vinga
 * @date 2024/11/23
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductSkuQuoteUpdateStatusDTO implements Serializable {
    @NotNull
    private Long quoteId;
    @NotNull
    private ProductSkuQuoteStatus status;
}

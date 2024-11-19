package com.lin.distribution.dto;

import com.lin.distribution.domain.ProductSkuQuoteDetail;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * @author vinga
 * @date 2024/11/15
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductSkuQuoteCreateDTO implements Serializable {
    private Long quoteId;
    @NotNull
    private Long customerId;
    @NotEmpty
    private String quoteCode;
    @NotNull
    private LocalDate effectiveStartDate;
    @NotNull
    private LocalDate effectiveEndDate;
    private String remark;
    private List<ProductSkuQuoteDetail> quoteDetails;
}

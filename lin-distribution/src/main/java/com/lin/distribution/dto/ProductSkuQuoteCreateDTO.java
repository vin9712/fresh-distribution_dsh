package com.lin.distribution.dto;

import com.lin.distribution.domain.ProductSkuQuoteDetail;
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
    private Long customerId;
    private String quoteCode;
    private LocalDate effectiveStartDate;
    private LocalDate effectiveEndDate;
    private String remark;
    private List<ProductSkuQuoteDetail> quoteDetails;
}

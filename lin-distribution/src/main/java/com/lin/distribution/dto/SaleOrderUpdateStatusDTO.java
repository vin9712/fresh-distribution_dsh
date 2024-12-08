package com.lin.distribution.dto;

import com.lin.distribution.constant.SaleOrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * @author vinga
 * @date 2024/12/08
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SaleOrderUpdateStatusDTO implements Serializable {
    @NotNull
    private List<Long> orderIds;
    @NotNull
    private Integer status;
}

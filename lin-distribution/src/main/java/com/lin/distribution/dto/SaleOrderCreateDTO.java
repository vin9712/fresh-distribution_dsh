package com.lin.distribution.dto;

import com.lin.distribution.domain.SaleOrderDetail;
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
 * @date 2024/12/03
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SaleOrderCreateDTO implements Serializable {
    private Long orderId;
    @NotNull
    private Long customerId;
    @NotNull
    private Long customerDeptId;
    @NotEmpty
    private String orderCode;
    @NotNull
    private LocalDate deliveryDate;
    private String remark;
    private List<SaleOrderDetail> orderDetails;
}

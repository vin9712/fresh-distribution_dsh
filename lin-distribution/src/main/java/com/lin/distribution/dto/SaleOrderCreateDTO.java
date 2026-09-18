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
    /**
     * 订单班次（biz_shift_type 字典值）：客户启用班次时必填，须在该配送点支持列表内；
     * 未启用班次的客户忽略该字段（后端归一化为空串）
     */
    private String shiftCode;
    @NotEmpty
    private String orderCode;
    @NotNull
    private LocalDate deliveryDate;
    private String remark;
    private List<SaleOrderDetail> orderDetails;
}

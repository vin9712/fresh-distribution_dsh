package com.lin.distribution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 按勾选订单生成送货单请求（销售订单列表页抽屉，配送日期可调整）
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryByOrdersDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 已确认销售订单ID集合（必填） */
    @NotEmpty(message = "请选择要生成送货单的订单")
    private List<Long> orderIds;

    /** 配送日期（可空，默认取订单配送日期） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deliveryDate;
}

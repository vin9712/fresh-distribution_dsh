package com.lin.distribution.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 订单批量验收 / 撤销验收 DTO（验收回归订单本体，废弃独立验收单）
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SaleOrderAcceptDTO implements Serializable {

    /** 销售订单ID集合 */
    @NotEmpty
    private List<Long> orderIds;
}

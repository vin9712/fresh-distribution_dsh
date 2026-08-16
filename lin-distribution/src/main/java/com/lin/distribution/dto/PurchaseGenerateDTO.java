package com.lin.distribution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 自动生成采购单请求（按配送日期汇总已确认订单）
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseGenerateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 采购归属日期（=订单配送日期） */
    @NotNull(message = "采购日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate orderDate;
}

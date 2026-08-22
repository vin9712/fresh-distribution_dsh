package com.lin.distribution.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 销售订单生成单据前汇总预览请求（列表页抽屉第一步）
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SaleOrderGeneratePreviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 已确认销售订单ID集合（必填） */
    @NotEmpty(message = "请选择要预览的订单")
    private List<Long> orderIds;
}

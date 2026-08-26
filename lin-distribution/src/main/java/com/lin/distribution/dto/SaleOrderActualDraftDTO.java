package com.lin.distribution.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 订单实收草稿批量保存 DTO（订单页实收自动保存）
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SaleOrderActualDraftDTO implements Serializable {

    /** 销售订单ID */
    @NotNull
    private Long orderId;

    /** 明细实收行 */
    @Valid
    @NotEmpty
    private List<Item> items;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Item implements Serializable {

        /** 订单明细ID */
        @NotNull
        private Long detailId;

        /** 实收数量（空 = 与下单数量一致） */
        private BigDecimal actualNum;

        /** 损耗原因（字典 biz_loss_reason） */
        private String lossReason;
    }
}

package com.lin.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 退货单质检 DTO（S14/T6：POST /order/return/{id}/inspect）。
 * 质检结论：1可再售(入库) 2不可再售(报损)——库存流水待库存模块，本期只记结论与备注（D-034）。
 *
 * @author dsh
 */
@Data
public class ReturnInspectDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 质检结论明细 */
    @Valid
    @NotEmpty(message = "质检结论不能为空")
    private List<Item> items;

    @Data
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 退货明细行ID */
        @NotNull(message = "退货明细行ID不能为空")
        private Long itemId;

        /** 质检结论：1可再售(入库) 2不可再售(报损) */
        @NotNull(message = "质检结论不能为空")
        private Integer qualityResult;

        /** 质检备注 */
        private String qualityNote;
    }
}

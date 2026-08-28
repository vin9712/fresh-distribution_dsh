package com.lin.distribution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 退货单保存 DTO（S14/T6：POST 新增 / PUT 改草稿共用）。
 * 退货数量上限=实收-累计已退、单价锁定原验收价均由后端校验/覆盖，前端不传价。
 *
 * @author dsh
 */
@Data
public class ReturnOrderSaveDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键（新增不传，修改必传） */
    private Long id;

    /** 原验收单ID（退货单价/数量来源） */
    @NotNull(message = "原验收单不能为空")
    private Long acceptanceId;

    /** 配送点ID（可空） */
    private Long customerDeptId;

    /** 退货日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate returnDate;

    /** 备注 */
    private String remark;

    /** 退货明细 */
    @NotEmpty(message = "退货明细不能为空")
    private List<Item> items;

    @Data
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 来源验收明细行 */
        @NotNull(message = "来源验收明细行不能为空")
        private Long acceptanceItemId;

        /** 退货数量（>0 且 ≤ 实收-累计已退） */
        @NotNull(message = "退货数量不能为空")
        private BigDecimal returnQuantity;
    }
}

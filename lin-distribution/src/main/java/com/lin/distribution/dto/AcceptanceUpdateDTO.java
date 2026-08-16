package com.lin.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 验收单录入/修改请求（仅草稿可改）
 * 金额与损耗由后端按实收数量×单价重算，客户端单价一律忽略。
 *
 * @author dsh
 */
@Data
public class AcceptanceUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 验收单ID */
    private Long id;

    /** 验收日期 */
    private LocalDate acceptDate;

    /** 备注 */
    private String remark;

    /** 明细（实收数量 + 损耗原因） */
    private List<Item> items;

    @Data
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 验收明细行ID */
        private Long id;

        /** 实收数量（可超送） */
        private BigDecimal actualQuantity;

        /** 损耗原因（损耗为负时必填） */
        private String lossReason;
    }
}

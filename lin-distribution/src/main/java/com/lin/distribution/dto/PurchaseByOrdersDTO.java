package com.lin.distribution.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 按勾选订单生成采购单请求（销售订单列表页抽屉）
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseByOrdersDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 已确认销售订单ID集合（必填） */
    @NotEmpty(message = "请选择要生成采购单的订单")
    private List<Long> orderIds;

    /** 供应商名称（直填，可空） */
    private String supplierName;

    /** 采购员（可空） */
    private String purchaser;
}

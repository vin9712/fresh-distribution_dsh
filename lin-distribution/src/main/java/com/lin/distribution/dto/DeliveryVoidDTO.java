package com.lin.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 送货单作废请求（S14/T4，详细设计 §5.2 voidDeliveryOrder）
 *
 * <p>作废原因走字典 {@code delivery_void_reason}（前端作废弹窗下拉）；
 * reasonCode=other 时 reasonNote 必填（后端二次校验）。</p>
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryVoidDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 作废原因编码（字典 delivery_void_reason，必填） */
    @NotBlank(message = "请选择作废原因")
    private String reasonCode;

    /** 补充说明（可空；reasonCode=other 时必填） */
    private String reasonNote;
}

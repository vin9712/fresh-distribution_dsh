package com.lin.distribution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 免纸送达登记（S14/T4，详细设计 §5.3 markDelivered(id, noPrint)）
 *
 * <p>触发条件：未打印（PENDING）直接标记送达；原因走字典 {@code delivery_no_print_reason}，
 * reasonCode=other 时 remark 必填（D-018）。已打印单送达无需传本参数。</p>
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryNoPrintDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 免纸原因编码（字典 delivery_no_print_reason；未打印送达时必填，条件校验在服务层） */
    private String reasonCode;

    /** 补充说明（reasonCode=other 时必填） */
    private String remark;
}

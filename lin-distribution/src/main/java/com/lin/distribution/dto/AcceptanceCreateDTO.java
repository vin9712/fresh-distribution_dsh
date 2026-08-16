package com.lin.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 验收单创建请求：按送货单生成验收单草稿（一单一验）
 *
 * @author dsh
 */
@Data
public class AcceptanceCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 送货单ID */
    private Long deliveryOrderId;
}

package com.lin.distribution.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 送货单打印日志（D-055：打印分界依据）
 *
 * <p>每次打印（总单/点单）记一条；判断「是否已打印」= 该 客户+日期+点 是否存在记录。
 * 已打印 = 配送后，订单变更需带标记（加单/换货/退货）。</p>
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPrintLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long customerId;
    private LocalDate deliveryDate;
    /** 配送点ID（点单打印）；总单打印为 NULL */
    private Long customerDeptId;
    private Date printTime;
    private String printBy;
    private Long templateId;
    private String createBy;
    private Date createTime;
}

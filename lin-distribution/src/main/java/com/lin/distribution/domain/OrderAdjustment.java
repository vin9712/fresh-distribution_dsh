package com.lin.distribution.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 订单加退换调整对象 order_adjustment
 *
 * @author dsh
 */
@Data
public class OrderAdjustment implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 原订单ID */
    private Long orderId;

    /** 原订单行ID（可空） */
    private Long orderItemId;

    /** 类型：1加单 2退单 3换货 */
    private Integer type;

    /** 原因 */
    private String reason;

    /** 调整日期（归属D天） */
    private LocalDate adjustDate;

    /** 明细说明JSON（换货含加/退两行） */
    private String detailJson;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}

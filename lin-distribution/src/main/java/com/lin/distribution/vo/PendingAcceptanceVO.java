package com.lin.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 待验收提醒 VO（蓝图 W0-3.2）：送货单已送达未验收；提醒级别 0无 1黄 2红
 *
 * @author dsh
 */
@Data
public class PendingAcceptanceVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long deliveryId;
    private String deliveryCode;
    private Long customerId;
    private String customerName;
    private Long deliveryPointId;
    private String deliveryPointName;
    private LocalDate deliveryDate;
    /** 最近打印时间 */
    private Date printTime;
    /** 提醒级别：0无 1黄色(打印满2h) 2红色(当日11:30后/已过期) */
    private Integer reminderLevel;
    private String reminderReason;
}

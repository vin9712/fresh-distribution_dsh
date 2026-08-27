package com.lin.distribution.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 验收撤回审计对象 t_acceptance_revoke_log
 * （订单-送货-验收链路详细设计 §三 ④ / Q16/D-014：撤回前主表+明细完整快照）
 *
 * @author dsh
 */
@Data
public class AcceptanceRevokeLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 验收单ID */
    private Long acceptanceId;

    /** 撤回前主表+明细完整JSON */
    private String snapshotJson;

    /** 撤回原因 */
    private String reason;

    /** 撤回人 */
    private String revokedBy;

    /** 撤回时间 */
    private Date revokedTime;
}

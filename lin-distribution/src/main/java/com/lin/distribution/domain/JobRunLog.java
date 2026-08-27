package com.lin.distribution.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 定时任务运行记录对象 t_job_run_log
 * （订单-送货-验收链路详细设计 §三 ⑥ / Q36/D-037：工作台告警数据源）
 *
 * @author dsh
 */
@Data
public class JobRunLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 任务名（DELIVERY_GENERATE 等） */
    private String jobName;

    /** 业务日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate bizDate;

    /** 状态：0成功 1失败 2部分失败(遗漏订单) */
    private Integer status;

    /** 异常摘要/遗漏提示 */
    private String message;

    /** 遗漏订单数 */
    private Integer warningCount;

    /** 运行时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date runTime;
}

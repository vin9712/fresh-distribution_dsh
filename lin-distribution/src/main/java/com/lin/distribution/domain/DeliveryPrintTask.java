package com.lin.distribution.domain;

import java.io.Serializable;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 送货单打印任务（P2/D-050：包内一张单）
 *
 * <p>状态机：0待打 → 1已预览 → 2打印中 → 3成功 / 4失败 / 5取消 / 6跳过。
 * 回执口径：成功才 {@code print_count+1} 并推进送货单状态（PRINTED）；失败不计数、可重试（retry_of 关联）。</p>
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPrintTask implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 状态：待打 */
    public static final int STATUS_PENDING = 0;
    /** 状态：已预览 */
    public static final int STATUS_PREVIEWED = 1;
    /** 状态：打印中 */
    public static final int STATUS_PRINTING = 2;
    /** 状态：成功 */
    public static final int STATUS_SUCCESS = 3;
    /** 状态：失败 */
    public static final int STATUS_FAILED = 4;
    /** 状态：取消 */
    public static final int STATUS_CANCELLED = 5;
    /** 状态：跳过 */
    public static final int STATUS_SKIPPED = 6;

    private Long id;
    /** 所属打印包 */
    private Long packageId;
    /** 送货单ID */
    private Long deliveryOrderId;
    /** 包内序号（队列执行序） */
    private Integer seqNo;
    /** 模板主键（本次生效，设为默认才写绑定） */
    private Long templateId;
    /** 份数（联数，本次生效） */
    private Integer copies;
    /** 打印机设备标识（预留，本地助手用） */
    private String deviceKey;
    private Integer status;
    /** 尝试次数（重试叠加） */
    private Integer attemptNo;
    /** 重试源任务ID（失败重试链） */
    private Long retryOfTaskId;
    /** 回执时间（成功才计次/推进状态） */
    private Date receiptTime;
    /** 失败原因 */
    private String errorMsg;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String remark;

    /** 展示字段：送货单号/客户名/配送点名/配送日期 */
    private String deliveryCode;
    private String customerName;
    private String customerDeptName;
    private Date deliveryDate;
    /** 展示字段：模板名/打印形态 */
    private String templateName;
    private String printForm;
}

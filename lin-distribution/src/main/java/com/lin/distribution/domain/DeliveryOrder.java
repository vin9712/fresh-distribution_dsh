package com.lin.distribution.domain;

import java.time.LocalDate;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.*;

/**
 * 送货单对象 t_delivery_order
 *
 * @author lin
 * @date 2024-12-11
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class DeliveryOrder extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 客户ID
     */
    @Excel(name = "客户ID")
    private Long customerId;

    /**
     * 配送点ID（t_customer_dept.id）
     */
    private Long deliveryPointId;

    /**
     * 所属配送批次（S14：t_delivery_batch.id）
     */
    private Long batchId;

    /**
     * 本单组单范围快照（总单时=CUSTOMER_DATE，默认 DELIVERY_POINT_DATE）
     */
    private String scopeType;

    /**
     * 单据种类：0正常单 1补充单(遗漏订单单独成单)
     */
    private Integer docKind;

    /**
     * 作废重建来源单ID（predecessor 链）
     */
    private Long predecessorId;

    /**
     * 打印次数
     */
    private Integer printCount;

    /** 最近打印时间（W0-3.2 待验收提醒：打印满2小时仍未验收→黄，配送日当天11:30后→红） */
    private Date printTime;

    /**
     * 作废原因（S14：状态=已作废时必填）
     */
    private String voidReason;

    /**
     * 作废人
     */
    private String voidBy;

    /**
     * 作废时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date voidTime;

    /**
     * 送货单编号
     */
    @Excel(name = "送货单编号")
    private String code;

    /**
     * 送货单状态：0待打印,1已打印,2已送达,3已作废
     */
    @Excel(name = "送货单状态：0待打印,1已打印,2已送达,3已作废")
    private Integer status;

    /**
     * 配送日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "配送日期", width = 30, dateFormat = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    /**
     * 逻辑删除
     */
    private Boolean isDeleted;

    /**
     * 版本号
     */
    @Version
    private Integer version;

    /**
     * 客户名称（列表展示用，查询时关联填充）
     */
    private String customerName;

    /**
     * 配送点名称（列表展示用，查询时关联填充）
     */
    private String customerDeptName;

    /** 待验收提醒级别（W0-3.2，列表实时计算非落库列）：0无 1黄(打印满2h) 2红(当日11:30后/已过期) */
    private Integer reminderLevel;

    /** 待验收提醒原因（与 reminderLevel 配套） */
    private String reminderReason;

}

package com.lin.distribution.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

/**
 * 客户每日配送批次对象 t_delivery_batch
 * （订单-送货-验收链路详细设计 §三 ①：第一层，客户+日期唯一，组单策略快照）
 *
 * @author dsh
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class DeliveryBatch extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 客户ID */
    private Long customerId;

    /** 配送日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    /** 组单策略快照：CUSTOMER_DATE/DELIVERY_POINT_DATE */
    private String scopeType;

    /** 跨订单/跨点相同商品合并快照 */
    private Boolean mergeSameItem;

    /** 模板绑定快照(打印层) */
    private Long templateId;

    /** 布局参数快照 column_count/rows_per_column */
    private String layoutJson;

    /** 状态：0有效 1关闭(当日确认不再生成) */
    private Integer status;

    /** 版本号 */
    @Version
    private Integer version;

    /** 逻辑删除 */
    private Boolean isDeleted;

    /** 客户名称（联表查询附带字段，非表列） */
    @TableField(exist = false)
    private String customerName;
}

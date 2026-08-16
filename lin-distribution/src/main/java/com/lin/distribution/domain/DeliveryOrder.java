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
     * 送货单编号
     */
    @Excel(name = "送货单编号")
    private String code;

    /**
     * 送货单状态：0待打印,1已打印,2已送达
     */
    @Excel(name = "送货单状态：0待打印,1已打印,2已送达")
    private Integer status;

    /**
     * 打印次数
     */
    private Integer printCount;

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

}

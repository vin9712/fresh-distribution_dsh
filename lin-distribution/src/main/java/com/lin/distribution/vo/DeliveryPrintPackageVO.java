package com.lin.distribution.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.lin.distribution.domain.DeliveryPrintTask;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 送货单打印包视图（P2/D-050）
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPrintPackageVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long batchId;
    private Long customerId;
    private String customerName;
    private Date deliveryDate;
    private String packageNo;
    private String mediaType;
    private Integer status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private Date previewTime;
    private String createBy;
    private Date createTime;
    private String remark;

    /** 包内任务清单（seq_no 升序） */
    @Builder.Default
    private List<DeliveryPrintTask> tasks = new ArrayList<>();

    /** 包内送货单合计数量/金额（任务对应送货单明细求和） */
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
}

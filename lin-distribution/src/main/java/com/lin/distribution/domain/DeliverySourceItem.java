package com.lin.distribution.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 送货来源明细对象 t_delivery_source_item
 * （订单-送货-验收链路详细设计 §三 ②：订单行→送货行分配，生成时冻结的应送台账）
 * 四项结构性用途：状态回写 / 幂等防重 / 作废释放 / 撤回判断；不做实收派生（D-026 修订）。
 *
 * @author dsh
 */
@Data
public class DeliverySourceItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 送货单ID */
    private Long deliveryId;

    /** 聚合送货行ID */
    private Long deliveryDetailId;

    /** 来源销售订单 */
    private Long saleOrderId;

    /** 来源订单行 */
    private Long saleOrderDetailId;

    /** 来源配送点(追溯/差异归属) */
    private Long customerDeptId;

    /** SKU ID（临时商品可空） */
    private Long skuId;

    /** 来源行品名快照 */
    private String productName;

    /** 本订单行分配到该送货行的数量 */
    private BigDecimal allocatedQuantity;

    /** 来源行单价快照(成本归属) */
    private BigDecimal unitPrice;

    /** 逻辑删除（作废送货单时释放来源用） */
    private Boolean isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 创建人 */
    private String createBy;
}

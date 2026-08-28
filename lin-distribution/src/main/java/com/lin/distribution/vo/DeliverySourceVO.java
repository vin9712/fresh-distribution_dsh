package com.lin.distribution.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 送货单来源视图（S14 §6.1/§八：聚合行 + 展开的来源订单/行/分配量）
 *
 * <p>历史单（无 source_item 台账）sources 为空列表，前端展示"—历史数据—"</p>
 *
 * @author dsh
 */
@Data
public class DeliverySourceVO {

    /** 送货明细行ID（聚合行） */
    private Long deliveryDetailId;

    /** 商品名称 */
    private String productName;

    /** 规格 */
    private String productSpec;

    /** 单位 */
    private String productUnit;

    /** 送货数量（聚合行） */
    private BigDecimal num;

    /** 单价 */
    private BigDecimal price;

    /** 小计 */
    private BigDecimal amount;

    /** 来源分配展开（按来源订单升序） */
    private List<SourceRow> sources;

    /** 来源分配行：订单行 → 本送货行的分配 */
    @Data
    public static class SourceRow {

        /** 来源销售订单ID */
        private Long saleOrderId;

        /** 来源订单号 */
        private String orderCode;

        /** 配送点ID（A类总单按点归属的差异定位） */
        private Long customerDeptId;

        /** 配送点名称 */
        private String customerDeptName;

        /** 分配到本送货行的数量 */
        private BigDecimal allocatedQuantity;

        /** 来源行单价快照 */
        private BigDecimal unitPrice;
    }
}

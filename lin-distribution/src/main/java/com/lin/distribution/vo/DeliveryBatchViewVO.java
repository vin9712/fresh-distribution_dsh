package com.lin.distribution.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户日总表视图行（S14 §6.1 / §八，D-027/D-028）：
 * 内部配货/采购视图——标准品名 + 总量 + 各配送点小计折叠，**不显示价格金额、不因价格拆行**。
 * 数据不落物理明细，由 t_delivery_source_item 实时聚合；历史单（无台账）回退送货明细行聚合。
 *
 * @author dsh
 */
@Data
public class DeliveryBatchViewVO {

    /** SKU ID（临时商品/历史数据可为 null） */
    private Long skuId;

    /** 标准品名（sku.name，无 sku 时回退快照品名） */
    private String productName;

    /** 规格快照（P0-A 修正：行键含规格，防同 SKU 不同规格错并） */
    private String spec;

    /** 单位快照（同上） */
    private String unit;

    /** 总量（各配送点合计） */
    private BigDecimal totalQuantity;

    /** 各配送点小计（按点名升序） */
    private List<DeptRow> depts = new ArrayList<>();

    /** 配送点小计行 */
    @Data
    public static class DeptRow {

        /** 配送点ID */
        private Long deptId;

        /** 配送点名称 */
        private String deptName;

        /** 小计数量 */
        private BigDecimal quantity;
    }

    /**
     * Mapper 扁平行（品名×配送点粒度），服务层聚合成 {@link DeliveryBatchViewVO}。
     * 独立顶层类便于 MyBatis resultType 映射。
     */
    @Data
    public static class Row {

        private Long skuId;

        private String productName;

        private String spec;

        private String unit;

        private Long deptId;

        private String deptName;

        private BigDecimal quantity;
    }
}

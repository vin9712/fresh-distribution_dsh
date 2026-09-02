package com.lin.distribution.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 矩阵总表视图（D-044/D-047/D-051，《送货单矩阵总表与批次视图设计》§三）
 *
 * <pre>
 * 行  = t_delivery_order_detail（批次内全部有效送货单的合并明细行，沿用 D-024 五元组：不同价必拆行）
 * 列  = t_delivery_batch.layout_json.columns（客户启用配送点快照，当日无单保留空列）
 * 格  = Σ t_delivery_source_item.allocated_quantity GROUP BY (delivery_detail_id, customer_dept_id)
 * </pre>
 *
 * <p>纸面<b>不打单价与金额</b>（D-046）：同一「品名+规格+单位」因不同价拆出的多行，
 * 用品名后的 {@code (档①)} 标记区分（{@link DeliveryMatrixLayout#displayProductName}）。</p>
 *
 * <p>页面与打印共用本视图（D-051），杜绝"预览与出纸不一致"。</p>
 *
 * @author dsh
 */
@Data
public class DeliveryMatrixVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 客户ID */
    private Long customerId;

    /** 客户名（别名优先，展示用） */
    private String customerName;

    /** 配送日期 yyyy-MM-dd */
    private String deliveryDate;

    /** 批次ID（无批次时为 null，此时布局按主数据实时推导） */
    private Long batchId;

    /** 批次组单策略快照 CUSTOMER_DATE / DELIVERY_POINT_DATE */
    private String scopeType;

    /** 打印形态 MATRIX / FLAT（取布局快照） */
    private String printForm;

    /** 每页点列数（横向列分页粒度） */
    private Integer colsPerPage;

    /** 点列块数 = ceil(列数 / colsPerPage)，1 表示无需列分页 */
    private Integer colBlocks;

    /** 布局版本号 */
    private Integer layoutVersion;

    /** true=批次无布局快照，本次按主数据实时推导（历史批次/未走过生成），只读不落库 */
    private Boolean layoutDerived = Boolean.FALSE;

    /** true=批次内全部单据均无 source_item 台账（历史单），点列打 — */
    private Boolean historyFallback = Boolean.FALSE;

    /** 列（含空列与临时补列），顺序即纸面列顺序 */
    private List<ColumnVO> columns = new ArrayList<>();

    /** 行（一个明细行一行） */
    private List<RowVO> rows = new ArrayList<>();

    /** 恒等式校验是否全部通过（detail.num == Σ该行各列格值） */
    private Boolean identityOk = Boolean.TRUE;

    /** 恒等式不一致明细（打印前自检，D-047） */
    private List<MismatchVO> mismatches = new ArrayList<>();

    /** 全表合计数量（各行合计之和） */
    private BigDecimal totalQuantity = BigDecimal.ZERO;

    /**
     * 矩阵列
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnVO implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 配送点ID */
        private Long deptId;

        /** 点编码快照 */
        private String code;

        /** 点名称快照 */
        private String name;

        /** 临时补列（点已停用但当日有单，D-053） */
        private Boolean adHoc;

        /** 本列是否有数据（全空列 false，纸面仍保留列） */
        private Boolean hasData;

        /** 所属列块（1 起，横向列分页用） */
        private Integer blockNo;
    }

    /**
     * 矩阵行 = 一条送货明细行
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowVO implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 送货明细行ID（行身份，D-047 不重新聚合） */
        private Long detailId;

        /** 所属送货单ID */
        private Long deliveryId;

        /** 所属送货单号 */
        private String deliveryCode;

        /** 单据种类：0正常单 1补充单 */
        private Integer docKind;

        /** SKU（临时商品可空） */
        private Long skuId;

        /** 标准品名（sku.name 优先，回退快照品名）——页面展示用 */
        private String productName;

        /** 客户叫法（无映射为 null）——打印品名优先（DESIGN 不变量 8） */
        private String customerAlias;

        /** 规格快照 */
        private String spec;

        /** 单位快照 */
        private String unit;

        /** 单价（纸面不打，仅内部定位价档与排查用） */
        private BigDecimal price;

        /** 送货数量（明细行 num，恒等式左值） */
        private BigDecimal num;

        /** 档分组键 {@link DeliveryMatrixLayout#groupKey} */
        private String groupKey;

        /** 本行档号（1 起；单档也记，便于排查） */
        private Integer tierRank;

        /** 该分组档位数（>1 才在品名后打档标） */
        private Integer tierCount;

        /** 品名纸面显示（标准品名 + 档标） */
        private String displayProductName;

        /** 备注（D-046/D-053：同名多行靠备注列标档位，纸面不打价）；无档标时为空白 */
        private String remark;

        /** 格值：deptId → 该点分配量（无该点数据则不含此 key） */
        private Map<Long, BigDecimal> cells = new LinkedHashMap<>();

        /** 格值按列顺序展开（与 columns 一一对应，null=空格）——打印与前端渲染用 */
        private List<BigDecimal> columnValues = new ArrayList<>();

        /** 行合计数量（= Σ格值；历史单回退时取 num） */
        private BigDecimal totalQuantity = BigDecimal.ZERO;

        /** 本行恒等式是否通过 */
        private Boolean identityOk = Boolean.TRUE;
    }

    /**
     * 恒等式不一致行（D-047 打印前自检）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MismatchVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long detailId;
        private String deliveryCode;
        private String productName;
        /** 明细行送货数量 */
        private BigDecimal num;
        /** 各列格值合计 */
        private BigDecimal cellSum;
    }

    /**
     * Mapper 扁平行：矩阵行（送货明细行）
     */
    @Data
    public static class DetailRow implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long detailId;
        private Long deliveryId;
        private String deliveryCode;
        private Integer docKind;
        private Long skuId;
        /** 明细行快照品名（我方品名） */
        private String productName;
        /** 标准品名（coalesce(sku.name, 快照品名)） */
        private String stdProductName;
        private String spec;
        private String unit;
        private BigDecimal num;
        private BigDecimal price;
    }

    /**
     * Mapper 扁平行：矩阵格（明细行 × 配送点 分配量合计）
     */
    @Data
    public static class CellRow implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long detailId;
        private Long deptId;
        private BigDecimal quantity;
    }
}

package com.lin.distribution.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson2.JSON;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 矩阵总表布局快照（D-045/D-053，《送货单矩阵总表与批次视图设计》§四）
 *
 * <p>序列化为 {@code t_delivery_batch.layout_json}（该列建表至今从未被写入，本期启用）。
 * 承载三件事：</p>
 * <ul>
 *   <li><b>列集合快照</b>：生成瞬间客户<b>启用</b>配送点（{@code valid=1}），当日无单的点保留空列；
 *       停用点不进列，但当日有单时以 {@code adHoc=true} 临时补列（D-053）；</li>
 *   <li><b>价档快照（append-only）</b>：同一「品名+规格+单位」下的不同单价按升序定 rank，
 *       补单/重建只追加新档、<b>已有档号永不重排</b>，保证已打印纸面的档标不变；</li>
 *   <li><b>分页参数</b>：{@code colsPerPage} 默认 6（单客户最多 5~6 点，A4 纵向一页放得下），
 *       超出走横向列分页兜底。</li>
 * </ul>
 *
 * <p>列顺序与点名的读取一律以本快照为准，不回落实时主数据——点改名/停用后历史总表重打版式不变
 * （对齐 D-016 批次期内快照不变）。</p>
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryMatrixLayout implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 打印形态：矩阵总表（A 类跨点合并） */
    public static final String FORM_MATRIX = "MATRIX";
    /** 打印形态：一维明细（B/C 类每点一单） */
    public static final String FORM_FLAT = "FLAT";
    /** 每页点列数默认值（A4 纵向约 6 列，设计 §四 列分页容量参考） */
    public static final int DEFAULT_COLS_PER_PAGE = 6;
    /** 圈码档标（GB2312 内含 ①~⑩，针式/A4 中文字库均可渲染） */
    private static final String[] CIRCLED = {"①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩"};

    /** 打印形态 MATRIX / FLAT */
    private String printForm;

    /** 每页点列数（横向列分页粒度，0/空=不分列页） */
    private Integer colsPerPage;

    /** 每页明细行数（0/空=不分页；针式固定 10 条由 W0-2.2 拆分配置管，此处仅记录矩阵期望值） */
    private Integer rowsPerPage;

    /** 布局版本号（列/档任一变化则 +1） */
    private Integer layoutVersion;

    /** 本次快照时间（yyyy-MM-dd'T'HH:mm:ss） */
    private String snapshotAt;

    /** 列快照（顺序即纸面列顺序，append-only） */
    private List<Column> columns;

    /** 价档快照（按「品名+规格+单位」分组，档号 append-only） */
    private List<TierGroup> priceTiers;

    public String toJson() {
        return JSON.toJSONString(this);
    }

    public static DeliveryMatrixLayout fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            DeliveryMatrixLayout layout = JSON.parseObject(json, DeliveryMatrixLayout.class);
            return layout == null ? null : layout.normalize();
        } catch (Exception e) {
            return null;
        }
    }

    /** 新建批次用的空骨架（列/档在生成后由 refreshLayout 填充） */
    public static DeliveryMatrixLayout newInstance() {
        return DeliveryMatrixLayout.builder()
                .printForm(FORM_MATRIX)
                .colsPerPage(DEFAULT_COLS_PER_PAGE)
                .rowsPerPage(0)
                .layoutVersion(1)
                .columns(new ArrayList<>())
                .priceTiers(new ArrayList<>())
                .build();
    }

    /** 反序列化后补齐空集合与默认值，防后续 NPE */
    public DeliveryMatrixLayout normalize() {
        if (columns == null) {
            columns = new ArrayList<>();
        }
        if (priceTiers == null) {
            priceTiers = new ArrayList<>();
        }
        if (printForm == null || printForm.isBlank()) {
            printForm = FORM_MATRIX;
        }
        if (colsPerPage == null || colsPerPage <= 0) {
            colsPerPage = DEFAULT_COLS_PER_PAGE;
        }
        if (layoutVersion == null || layoutVersion <= 0) {
            layoutVersion = 1;
        }
        return this;
    }

    /** 档分组键：临时商品（无 sku）退化用快照品名，规格与单位必进键防误并 */
    public static String groupKey(Long skuId, String productName, String spec, String unit) {
        String base = skuId != null ? "sku:" + skuId : "name:" + (productName == null ? "" : productName);
        return base + "|" + (spec == null ? "" : spec) + "|" + (unit == null ? "" : unit);
    }

    /** 单价比较键：去尾零（3.20 与 3.2 同档，与生成侧五元组合并口径一致） */
    public static String priceKey(BigDecimal price) {
        return price == null ? "0" : price.stripTrailingZeros().toPlainString();
    }

    /** 档标：1~10 用圈码，超出用阿拉伯数字 */
    public static String tierLabel(int rank) {
        return rank >= 1 && rank <= CIRCLED.length ? CIRCLED[rank - 1] : String.valueOf(rank);
    }

    /**
     * 品名纸面显示（D-046/D-053：矩阵不打单价；D-053 档位标注改存备注列，品名本身保持干净）
     *
     * @param baseName 品名（客户别名优先后的展示名）
     * @return 干净品名（不再拼档标）
     */
    public static String displayProductName(String baseName) {
        return baseName == null ? "" : baseName;
    }

    /**
     * 档位标注（D-046/D-053 修订：档位标在<b>备注列</b>，非品名后）。
     * 批次内该「品名+规格+单位」档位数 &gt;1 才返回标注，单档返回空串（纸面保持干净）。
     *
     * @param rank     本行档号（1 起）
     * @param tierCount 该档分组内的档位数
     * @return 如 "档①"；单档或无档号返回 ""
     */
    public static String tierRemark(Integer rank, Integer tierCount) {
        if (rank == null || tierCount == null || tierCount <= 1) {
            return "";
        }
        return "档" + tierLabel(rank);
    }

    /** 按档分组键取分组（无则 null） */
    public TierGroup tierGroup(String groupKey) {
        if (groupKey == null) {
            return null;
        }
        return priceTiers.stream().filter(g -> groupKey.equals(g.getGroupKey())).findFirst().orElse(null);
    }

    /**
     * 配送点列（纸面一列）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Column implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 配送点ID（t_customer_dept.id） */
        private Long deptId;

        /** 点编码快照（列排序依据，仅记录用） */
        private String code;

        /** 点名称快照（不回落实时主数据） */
        private String name;

        /** 是否临时补列（点已停用但当日有单，D-053） */
        private Boolean adHoc;

        public boolean isAdHoc() {
            return Boolean.TRUE.equals(adHoc);
        }
    }

    /**
     * 价档分组（同一「品名+规格+单位」下的多个单价档）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierGroup implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 分组键 {@link #groupKey} */
        private String groupKey;

        /** 品名快照（展示与排查用） */
        private String productName;

        /** 规格快照 */
        private String spec;

        /** 单位快照 */
        private String unit;

        /** 档位（rank 升序，档号 append-only 不重排） */
        private List<Tier> tiers;

        public List<Tier> safeTiers() {
            if (tiers == null) {
                tiers = new ArrayList<>();
            }
            return tiers;
        }
    }

    /**
     * 单个价档
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tier implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 档号（1 起，批次内稳定，永不重排） */
        private Integer rank;

        /** 单价快照（纸面不打，仅内部定位价档用） */
        private BigDecimal price;

        /** 单价比较键 {@link #priceKey} */
        private String priceKey;
    }
}

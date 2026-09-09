package com.lin.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 当日打印清单（PT-2，《客户日报表打印优化设计》§3.2）：
 * 按 配送日期 聚合当天全部客户应打单据——每客户一张总单（matrix）+ 每配送点一张点单（point）。
 *
 * <p>取数口径与点单一致（订单明细 o.status&gt;=1 且未删除）；printed 取 t_delivery_print_log 打印分界。</p>
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintManifestVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 客户ID */
    private Long customerId;

    /** 客户名称（alias 优先，与点单表头口径一致） */
    private String customerName;

    /** 总单打印主体键 matrix:{customerId}:{deliveryDate} */
    private String matrixBizKey;

    /** 总单是否已打印（打印分界） */
    private Boolean matrixPrinted;

    /** 该客户各配送点（当天实际有单的点） */
    @Builder.Default
    private List<PointEntry> points = new ArrayList<>();

    /** 点单条目 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 配送点ID（可为 null=订单未分配点，归入「未分配」组） */
        private Long deptId;

        /** 配送点名称（null 显示「未分配」） */
        private String deptName;

        /** 点单打印主体键 point:{customerId}:{deptId}:{deliveryDate} */
        private String bizKey;

        /** 是否已打印（打印分界） */
        private Boolean printed;

        /** 应送合计 = Σ num */
        private BigDecimal totalNum;
    }

    /** Mapper 扁平行（客户×点×数量，服务层聚合成 VO） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long customerId;
        private String customerName;
        private Long deptId;
        private String deptName;
        private BigDecimal num;
    }
}

package com.lin.distribution.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.lin.distribution.domain.SaleOrderDetail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 点单视图（按 客户+配送日期 全点返回，D-055 收尾：客户日总表页点单口径分 tab 展示）
 *
 * <p>一个配送点一个分组：组内=该点订单明细行（含加单/换货/退货标记），组头信息用于 tab 标签
 * （点名 + 应送/实收合计徽标）。分组由服务层按订单明细实际归属的配送点聚合——
 * 「按当天实际情况」，当天没有单的点不出 tab。</p>
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPointViewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 配送点ID（点单打印 / 生成验收单的主体） */
    private Long deptId;

    /** 配送点名称 */
    private String deptName;

    /** 该点订单明细行数（含标记行） */
    private Integer rowCount;

    /** 应送合计 = Σ num（退货行 num 已归 0） */
    private BigDecimal totalNum;

    /** 实收合计 = Σ actual_num（未验收时为 0/空） */
    private BigDecimal totalActual;

    /** 该点订单明细行（含标记，按 sort 升序） */
    private List<SaleOrderDetail> rows = new ArrayList<>();
}

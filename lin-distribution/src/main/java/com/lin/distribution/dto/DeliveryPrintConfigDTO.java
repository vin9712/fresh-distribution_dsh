package com.lin.distribution.dto;

import lombok.Data;

import java.util.List;

/**
 * 送货单打印拆分配置保存请求（W0-2.2）
 *
 * <p>用于调整未打印送货单的拆分方式、输出介质、分页行数与打印顺序（排序）。
 * 后端校验规则（{@link com.lin.distribution.constant.DeliveryPrintRule}）：
 * 跨点合单仅 A4；针式仅单点固定每页 10 条；detailOrder 必须为该单有效明细的全排列。</p>
 *
 * @author dsh
 */
@Data
public class DeliveryPrintConfigDTO {

    /** 拆分方式：DEFAULT_PER_DEPT/CROSS_POINT_MERGE/MAX_ROWS_SPLIT（缺省按批次策略推导） */
    private String splitMode;

    /** 输出介质：A4/DOT_MATRIX（缺省 A4） */
    private String mediaType;

    /** 分页行数（针式单点固定 10；缺省 10） */
    private Integer rowsPerPage;

    /** 打印顺序：明细ID有序全排列（缺省=按系统自动生成顺序） */
    private List<Long> detailOrder;

    /** 变更说明（写入版本记录） */
    private String changeNote;
}

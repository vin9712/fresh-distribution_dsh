package com.lin.distribution.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 打印预览记录对象 t_print_preview_log（蓝图 W0-4.4：每次正式打印前必须有预览记录）
 *
 * @author dsh
 */
@Data
public class PrintPreviewLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 模板ID */
    private Long templateId;

    /** 送货单ID（可空，汇总预览为空） */
    private Long deliveryOrderId;

    /** 操作人 */
    private String operator;

    /** 预览时间 */
    private Date previewTime;
}

package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 退货明细对象 t_return_item
 * （订单-送货-验收链路详细设计 §三 ⑤：数量上限=实收-累计已退，单价=原验收单价不可改）
 *
 * @author dsh
 */
@Data
public class ReturnItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 退货单ID */
    private Long returnId;

    /** 来源验收明细行 */
    private Long acceptanceItemId;

    /** SKU ID（临时商品可空） */
    private Long skuId;

    /** SKU 编码（关联 t_product_sku.code，列表展示用，非表字段） */
    private String skuCode;

    /** 商品名称快照 */
    @Excel(name = "商品名称")
    private String productName;

    /** 规格快照 */
    private String productSpec;

    /** 单位快照 */
    private String productUnit;

    /** 退货数量 */
    @Excel(name = "退货数量")
    private BigDecimal returnQuantity;

    /** 单价快照(=原验收单价,不可改) */
    private BigDecimal unitPrice;

    /** 退货金额(退货数量×单价) */
    @Excel(name = "退货金额")
    private BigDecimal amount;

    /** 质检结论：1可再售(入库) 2不可再售(报损) */
    private Integer qualityResult;

    /** 质检备注 */
    private String qualityNote;
}

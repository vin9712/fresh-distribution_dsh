package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import com.lin.distribution.vo.DeliverySourceVO;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 验收单明细对象 acceptance_item
 * 实收金额=actual_quantity×unit_price（后端重算）；
 * 差异=实收−送货（正超收/负短收，双向差异均必填原因，S14 v1.1 修订）。
 * S14：loss_quantity 更名 difference_quantity，实体字段同步更名为 differenceQuantity，
 *      保留 @Deprecated getLossQuantity()/setLossQuantity() 转发一个版本以兼容旧 JSON 报文。
 *
 * @author dsh
 */
@Data
public class AcceptanceItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 验收单ID */
    @Excel(name = "验收单ID")
    private Long acceptanceId;

    /** 送货单明细ID（D-055 后保留历史兼容，新行以订单明细为维度） */
    @Excel(name = "送货单明细ID")
    private Long deliveryItemId;

    /** D-055：来源订单明细ID（应送行=订单明细，含加单/换货/退货标记） */
    private Long saleOrderDetailId;

    /** 明细所属配送点（S14：A类总单按点展开录入即归属，B/C类也填） */
    private Long customerDeptId;

    /** 配送点名称（列表展示用，查询时关联填充，非表字段） */
    private String customerDeptName;

    /** SKU（临时商品可空） */
    @Excel(name = "SKU ID")
    private Long skuId;

    /** SKU 编码（关联 t_product_sku.code；扫码枪扫码定位用，非表字段） */
    @Excel(name = "商品编码")
    private String skuCode;

    /** 商品名称快照 */
    @Excel(name = "商品名称")
    private String productName;

    /** 规格快照 */
    @Excel(name = "商品规格")
    private String productSpec;

    /** 单位快照 */
    @Excel(name = "商品单位")
    private String productUnit;

    /** 送货数量（基线=送货单明细数量） */
    @Excel(name = "送货数量")
    private BigDecimal deliveredQuantity;

    /** 实收数量（可超送） */
    @Excel(name = "实收数量")
    private BigDecimal actualQuantity;

    /** 单价快照 */
    @Excel(name = "单价")
    private BigDecimal unitPrice;

    /** 验收差异 = 实收−送货（正超收/负短收） */
    @Excel(name = "差异数量")
    private BigDecimal differenceQuantity;

    /** 差异原因类型：1短收(acceptance_shortfall_reason) 2超收(acceptance_overage_reason) */
    private Integer reasonType;

    /** 差异原因（短收/超收字典值） */
    @Excel(name = "差异原因")
    private String lossReason;

    /** 实收金额（实收×单价） */
    @Excel(name = "实收金额")
    private BigDecimal actualAmount;

    /** 排序 */
    @Excel(name = "排序")
    private Integer sort;

    /** 来源对照（S14 §八：来源订单号/下单数量/下单单价，join t_delivery_source_item；历史单为空列表，前端展示“—历史数据—”）——非持久化字段 */
    private transient List<DeliverySourceVO.SourceRow> sources;

    /** 累计已退数量（退货单页面可退量=实收−累计已退，含草稿/已提交占用；status=3 已完成不占用）——非持久化字段 */
    private transient BigDecimal returnedQuantity;

    /** 来源订单号（AC-6，《验收模块订单明细视角重构设计》：新口径验收行=订单明细行，按 sale_order_detail_id 反查）——非持久化字段 */
    private transient String orderCode;

    /** 变更标记（OA：1加单 2换货 3退货，按 sale_order_detail_id 反查订单明细，供前端行首 tag）——非持久化字段 */
    private transient Integer changeType;

    /** 变更备注（OA：D-055 标记行说明，如「换货 土豆→番茄」）——非持久化字段 */
    private transient String changeRemark;

    /**
     * @deprecated S14 已更名 {@link #differenceQuantity}，保留一个版本兼容旧 JSON/调用方
     */
    @Deprecated
    public BigDecimal getLossQuantity() {
        return differenceQuantity;
    }

    /**
     * @deprecated S14 已更名 {@link #differenceQuantity}，保留一个版本兼容旧 JSON/调用方
     */
    @Deprecated
    public void setLossQuantity(BigDecimal lossQuantity) {
        this.differenceQuantity = lossQuantity;
    }
}

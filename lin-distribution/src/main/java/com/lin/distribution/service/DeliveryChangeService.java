package com.lin.distribution.service;

import com.lin.distribution.domain.SaleOrderDetail;

import java.math.BigDecimal;
import java.util.List;

/**
 * 配送后订单变更服务（D-055：打印后=配送后，原订单数据不变，变更以标记附加在订单明细上）
 *
 * <p>变更类型：加单（补充）/换货（一退一进，同 change_group）/退货。</p>
 *
 * @author dsh
 */
public interface DeliveryChangeService {

    /**
     * 配送后加单（补充单据）：在指定订单的配送点新增一行 change_type=1（填写应收 num + 实收 actualNum）。
     *
     * @param orderId        订单ID（status=1 已确认）
     * @param skuId          商品ID（临时商品可空）
     * @param productName    品名
     * @param spec           规格
     * @param unit           单位
     * @param num            应收（应送）
     * @param price          单价（快照，缺省取当前报价）
     * @param actualNum      实收（配送后现场确认；可空=默认=应收）
     * @param remark         说明
     * @return 新增明细行
     */
    SaleOrderDetail addSupplement(Long orderId, Long skuId, String productName, String spec, String unit,
                                  BigDecimal num, BigDecimal price, BigDecimal actualNum, String remark);

    /**
     * 配送后换货（A换B）：原明细行标 change_type=3（退货，应送/实收归0），
     * 新明细行 change_type=2（换货），同 change_group。
     *
     * @param orderId        订单ID
     * @param targetDetailId 被换明细行ID（原行）
     * @param skuId          新商品ID（可空）
     * @param productName    新商品名（换入）
     * @param spec           规格
     * @param unit           单位
     * @param num            换入应收
     * @param actualNum      换入实收（默认=应收）
     * @param remark         说明
     * @return 换货新增行 + 被换行
     */
    List<SaleOrderDetail> exchange(Long orderId, Long targetDetailId, Long skuId, String productName,
                                   String spec, String unit, BigDecimal num, BigDecimal actualNum, String remark);

    /**
     * 配送后退货：原明细行标 change_type=3（应送/实收归0）。
     *
     * @param orderId        订单ID
     * @param targetDetailId 被退明细行ID
     * @param remark         说明
     * @return 更新后的明细行
     */
    SaleOrderDetail returnLine(Long orderId, Long targetDetailId, String remark);
}

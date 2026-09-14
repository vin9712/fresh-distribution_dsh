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
     * @param price          换入单价（快照，可空=取被换行原单价，等价换；避免换入行记成 0 元）
     * @param actualNum      换入实收（默认=应收）
     * @param remark         说明
     * @return 换货新增行 + 被换行
     */
    List<SaleOrderDetail> exchange(Long orderId, Long targetDetailId, Long skuId, String productName,
                                   String spec, String unit, BigDecimal num, BigDecimal price,
                                   BigDecimal actualNum, String remark);

    /**
     * 配送后退货：原明细行标 change_type=3（应送/实收归0）。
     *
     * @param orderId        订单ID
     * @param targetDetailId 被退明细行ID
     * @param remark         说明
     * @return 更新后的明细行
     */
    SaleOrderDetail returnLine(Long orderId, Long targetDetailId, String remark);

    /**
     * 配送后变更回退（OA，《订单页一键验收链路设计》§4.4）：
     * <ul>
     *   <li>加单行（type=1）：删除该行；</li>
     *   <li>退货行（type=3）：恢复本行（change_type=0，num/actual_num 取 change_original_num 快照）；
     *       若属换货组，同组换入行（type=2）一并删除；</li>
     *   <li>换入行（type=2）：整组回退（删换入行 + 恢复被换行）；</li>
     * </ul>
     * 验收单为草稿时同步清理对应验收行并重算总额（恢复行由 syncMissingItems 重新生成）；
     * 验收单已提交则拒绝（请先撤销）。
     *
     * @param orderId  订单ID
     * @param detailId 带变更标记的明细行ID
     * @return 被回退的明细行
     */
    SaleOrderDetail revokeChange(Long orderId, Long detailId);
}

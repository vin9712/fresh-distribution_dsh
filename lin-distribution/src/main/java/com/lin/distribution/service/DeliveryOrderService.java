package com.lin.distribution.service;

import java.time.LocalDate;
import java.util.List;

import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.dto.DeliveryByOrdersDTO;

/**
 * 送货单据Service接口
 *
 * @author lin
 * @date 2024-12-11
 */
public interface DeliveryOrderService {
    /**
     * 查询送货单据
     *
     * @param id 送货单据主键
     * @return 送货单据
     */
    DeliveryOrder selectDeliveryOrderById(Long id);

    /**
     * 查询送货单明细列表（按商品合并行）
     *
     * @param deliveryId 送货单主键
     * @return 送货单明细集合
     */
    List<DeliveryOrderDetail> selectDetailListByDeliveryId(Long deliveryId);

    /**
     * 查询送货单据列表
     *
     * @param deliveryOrder 送货单据
     * @return 送货单据集合
     */
    List<DeliveryOrder> selectDeliveryOrderList(DeliveryOrder deliveryOrder);

    /**
     * 新增送货单据
     *
     * @param deliveryOrder 送货单据
     * @return 结果
     */
    int insertDeliveryOrder(DeliveryOrder deliveryOrder);

    /**
     * 修改送货单据
     *
     * @param deliveryOrder 送货单据
     * @return 结果
     */
    int updateDeliveryOrder(DeliveryOrder deliveryOrder);

    /**
     * 批量删除送货单据
     *
     * @param ids 需要删除的送货单据主键集合
     * @return 结果
     */
    int deleteDeliveryOrderByIds(Long[] ids);

    /**
     * 删除送货单据信息
     *
     * @param id 送货单据主键
     * @return 结果
     */
    int deleteDeliveryOrderById(Long id);

    /**
     * 按配送日期生成送货单（DESIGN.md §7.2）
     * 粒度：客户 + 配送点 + 配送日期；明细按商品合并（不含订单号）。
     * 仅包含已确认（CONFIRMED）订单；该日期已存在送货单则拒绝重复生成。
     *
     * @param deliveryDate 配送日期
     * @return 本次生成的送货单列表
     */
    List<DeliveryOrder> generateByDeliveryDate(LocalDate deliveryDate);

    /**
     * 按勾选订单生成送货单（销售订单列表页抽屉，Phase 2）
     * 粒度：客户 + 配送点；配送日期可调整（默认取订单配送日期）；
     * 订单不可重复生成（t_delivery_order_detail.order_id 唯一键 + 前置校验）。
     *
     * @param dto 生成请求（orderIds + 可空 deliveryDate）
     * @return 本次生成的送货单列表
     */
    List<DeliveryOrder> generateByOrderIds(DeliveryByOrdersDTO dto);

    /**
     * 标记打印：print_count + 1，状态 → 已打印（已送达不可打印）
     *
     * @param id 送货单主键
     * @return 更新后的送货单
     */
    DeliveryOrder markPrinted(Long id);

    /**
     * 标记送达：状态 → 已送达，同组（客户+配送点+配送日）已确认订单 → DELIVERED
     *
     * @param id 送货单主键
     * @return 更新后的送货单
     */
    DeliveryOrder markDelivered(Long id);
}

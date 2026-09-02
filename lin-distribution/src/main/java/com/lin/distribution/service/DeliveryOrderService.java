package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.dto.DeliveryNoPrintDTO;
import com.lin.distribution.vo.DeliverySourceVO;

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
     * 送货单来源视图（S14 §6.1/§八）：聚合行 + 展开的来源订单/行/分配量。
     *
     * <p>历史单（无 source_item 台账）返回聚合行、sources 为空列表，前端展示“—历史数据—”。</p>
     *
     * @param deliveryId 送货单主键
     * @return 来源视图集合（每聚合行一条）
     */
    List<DeliverySourceVO> selectDeliverySources(Long deliveryId);

    /**
     * 查询送货单据列表
     *
     * @param deliveryOrder 送货单据
     * @return 送货单据集合
     */
    List<DeliveryOrder> selectDeliveryOrderList(DeliveryOrder deliveryOrder);

    /**
     * 批次分组聚合分页（D-043）：按 客户+配送日期 分组，主行聚合张数/状态数/合计/打印形态/提醒最高级。
     *
     * @param deliveryOrder 筛选条件（customerId/deliveryPointId/status/deliveryDate/scopeType 等）
     * @return 批次分组聚合集合
     */
    List<com.lin.distribution.vo.DeliveryBatchPageVO> selectBatchPage(DeliveryOrder deliveryOrder);

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
     * 标记打印：print_count + 1，状态 → 已打印（已送达不可打印）
     *
     * @param id 送货单主键
     * @return 更新后的送货单
     */
    DeliveryOrder markPrinted(Long id);

    /**
     * 标记送达（兼容旧调用）：等价 markDelivered(id, null)，未打印单送达将被拒绝（缺少免纸原因）
     *
     * @param id 送货单主键
     * @return 更新后的送货单
     */
    DeliveryOrder markDelivered(Long id);

    /**
     * 作废送货单（S14/T4，DESIGN.md §5.2）：状态 → 已作废（终态），来源分配软删释放订单。
     * 准入：PENDING/PRINTED（已打印作废重开时纸面单号失效由前端警示）；
     * 已有 SUBMITTED 验收或任一来源订单 SETTLED → 拒绝。
     *
     * @param id         送货单主键
     * @param reasonCode 作废原因编码（字典 delivery_void_reason）
     * @param reasonNote 补充说明（reasonCode=other 时必填）
     */
    void voidDeliveryOrder(Long id, String reasonCode, String reasonNote);

    /**
     * 标记送达（S14/T4 改造，DESIGN.md §5.3）：状态 → 已送达，仅回写来源台账命中的订单。
     * 未打印（PENDING）直接送达时必须登记免纸原因（字典 delivery_no_print_reason，other 必填说明，
     * D-018），拼接进单据 remark 留痕；已打印（PRINTED）送达时 noPrint 传 null 即可。
     *
     * @param id      送货单主键
     * @param noPrint 免纸送达登记（未打印送达时必填，其余可空）
     * @return 更新后的送货单
     */
    DeliveryOrder markDelivered(Long id, DeliveryNoPrintDTO noPrint);
}

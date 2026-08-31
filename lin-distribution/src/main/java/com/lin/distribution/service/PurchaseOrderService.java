package com.lin.distribution.service;

import com.lin.distribution.domain.PurchaseItem;
import com.lin.distribution.domain.PurchaseModifyLog;
import com.lin.distribution.domain.PurchaseOrder;
import com.lin.distribution.dto.PurchaseByOrdersDTO;
import com.lin.distribution.dto.PurchaseGenerateDTO;

import java.util.List;

/**
 * 采购单Service接口
 *
 * @author dsh
 */
public interface PurchaseOrderService {
    /**
     * 查询采购单
     *
     * @param id 采购单主键
     * @return 采购单
     */
    PurchaseOrder selectPurchaseOrderById(Long id);

    /**
     * 查询采购单列表
     *
     * @param purchaseOrder 采购单
     * @return 采购单集合
     */
    List<PurchaseOrder> selectPurchaseOrderList(PurchaseOrder purchaseOrder);

    /**
     * 查询采购单明细列表
     *
     * @param purchaseId 采购单ID
     * @return 采购单明细集合
     */
    List<PurchaseItem> selectPurchaseItemListByPurchaseId(Long purchaseId);

    /**
     * 按配送日期自动生成采购单（汇总已确认订单，按 sku_id 合并数量）
     *
     * @param dto 生成请求
     * @return 生成的采购单
     */
    PurchaseOrder generateByOrderDate(PurchaseGenerateDTO dto);

    /**
     * 按勾选订单生成采购单（销售订单列表页抽屉，Phase 2）
     * 仅汇总已确认订单明细，按 sku_id 合并数量；订单不可重复生成（source_order_ids 重叠校验）。
     *
     * @param dto 生成请求（orderIds + 供应商/采购员）
     * @return 生成的采购单
     */
    PurchaseOrder generateByOrderIds(PurchaseByOrdersDTO dto);

    /**
     * 手工创建采购单（含明细）
     *
     * @param purchaseOrder 采购单（含 items）
     * @return 结果
     */
    int createPurchase(PurchaseOrder purchaseOrder);

    /**
     * 修改采购单（仅草稿，明细先删后插）
     *
     * @param purchaseOrder 采购单（含 items）
     * @return 结果
     */
    int updatePurchase(PurchaseOrder purchaseOrder);

    /**
     * 确认采购单（草稿→已确认）
     *
     * @param id 采购单主键
     * @return 结果
     */
    int confirm(Long id);

    /**
     * 已确认采购单直接调整数量/成本（蓝图 W0-2.5「已确认采购纠错」）：
     * 允许对已确认单修改明细数量或采购单价，重算总额，并记录调整前后金额与明细快照审计日志；
     * 调整后报表/经营概览以实时采购数据为准（同步重算受影响周期）。
     *
     * @param purchaseOrder 采购单（含 id + items，明细携带原 id 用于定位）
     * @return 调整后的采购单
     */
    PurchaseOrder adjustConfirmedPurchase(PurchaseOrder purchaseOrder);

    /**
     * 查询某采购单的调整审计日志列表（W0-2.5）
     *
     * @param purchaseId 采购单主键
     * @return 调整日志集合
     */
    List<PurchaseModifyLog> selectModifyLogsByPurchaseId(Long purchaseId);

    /**
     * 入库采购单（已确认→已入库）
     *
     * @param id 采购单主键
     * @return 结果
     */
    int stockIn(Long id);

    /**
     * 批量入库（S2-2.2 批量确认成本）：仅已确认采购单可入库，遇非已确认单抛异常整体回滚
     *
     * @param ids 采购单主键集合
     * @return 成功入库数
     */
    int batchStockIn(Long[] ids);

    /**
     * 供应商补录（S2-2.2）：草稿/已确认采购单补录或修正供应商与采购员
     * （蓝图「供应商补录」；已确认单补录属纠错，@Log 审计）
     *
     * @param id            采购单主键
     * @param supplierId    供应商ID（可空）
     * @param supplierName  供应商名称（直填，可空但与 supplierId 至少其一）
     * @param purchaser     采购员（可空）
     * @return 结果
     */
    int backfillSupplier(Long id, Long supplierId, String supplierName, String purchaser);

    /**
     * 批量删除采购单（仅草稿）
     *
     * @param ids 需要删除的采购单主键集合
     * @return 结果
     */
    int deletePurchaseOrderByIds(Long[] ids);
}

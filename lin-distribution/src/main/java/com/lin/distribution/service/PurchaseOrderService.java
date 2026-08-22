package com.lin.distribution.service;

import com.lin.distribution.domain.PurchaseItem;
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
     * 入库采购单（已确认→已入库）
     *
     * @param id 采购单主键
     * @return 结果
     */
    int stockIn(Long id);

    /**
     * 批量删除采购单（仅草稿）
     *
     * @param ids 需要删除的采购单主键集合
     * @return 结果
     */
    int deletePurchaseOrderByIds(Long[] ids);
}

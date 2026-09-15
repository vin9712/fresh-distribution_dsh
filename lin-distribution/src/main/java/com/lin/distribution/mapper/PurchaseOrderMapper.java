package com.lin.distribution.mapper;

import com.lin.distribution.domain.PurchaseOrder;

import java.time.LocalDate;
import java.util.List;

/**
 * 采购单Mapper接口
 *
 * @author dsh
 */
public interface PurchaseOrderMapper {
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
     * 新增采购单
     *
     * @param purchaseOrder 采购单
     * @return 结果
     */
    int insertPurchaseOrder(PurchaseOrder purchaseOrder);

    /**
     * 修改采购单
     *
     * @param purchaseOrder 采购单
     * @return 结果
     */
    int updatePurchaseOrder(PurchaseOrder purchaseOrder);

    /**
     * 修改采购单单头默认供应商/采购员/备注（允许显式置空）
     *
     * @param purchaseOrder 采购单（id 必填）
     * @return 结果
     */
    int updatePurchaseHeader(PurchaseOrder purchaseOrder);

    /**
     * 批量删除采购单
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deletePurchaseOrderByIds(Long[] ids);

    /**
     * 查询某采购日期当前有效（非作废）采购单，最新一张（D-056：一天一单，作废后重建取最新）
     *
     * @param orderDate 采购日期（=配送日期）
     * @return 采购单（不存在返回 null）
     */
    PurchaseOrder selectActiveByOrderDate(LocalDate orderDate);
}

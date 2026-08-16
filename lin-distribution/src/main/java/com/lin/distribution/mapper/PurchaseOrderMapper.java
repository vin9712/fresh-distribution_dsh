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
     * 批量删除采购单
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deletePurchaseOrderByIds(Long[] ids);

    /**
     * 查询指定配送日期的已确认订单ID（去重），用于采购单 source_order_ids
     *
     * @param deliveryDate 配送日期
     * @return 订单ID集合
     */
    List<Long> selectSourceOrderIdsByDeliveryDate(LocalDate deliveryDate);
}

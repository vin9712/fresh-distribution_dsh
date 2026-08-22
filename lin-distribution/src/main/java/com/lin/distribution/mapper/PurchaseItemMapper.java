package com.lin.distribution.mapper;

import com.lin.distribution.domain.PurchaseItem;

import java.time.LocalDate;
import java.util.List;

/**
 * 采购单明细Mapper接口
 *
 * @author dsh
 */
public interface PurchaseItemMapper {
    /**
     * 查询采购单明细
     *
     * @param id 采购单明细主键
     * @return 采购单明细
     */
    PurchaseItem selectPurchaseItemById(Long id);

    /**
     * 查询采购单明细列表
     *
     * @param purchaseItem 采购单明细
     * @return 采购单明细集合
     */
    List<PurchaseItem> selectPurchaseItemList(PurchaseItem purchaseItem);

    /**
     * 新增采购单明细
     *
     * @param purchaseItem 采购单明细
     * @return 结果
     */
    int insertPurchaseItem(PurchaseItem purchaseItem);

    /**
     * 修改采购单明细
     *
     * @param purchaseItem 采购单明细
     * @return 结果
     */
    int updatePurchaseItem(PurchaseItem purchaseItem);

    /**
     * 批量删除采购单明细
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deletePurchaseItemByIds(Long[] ids);

    /**
     * 根据采购单ID查询明细列表
     *
     * @param purchaseId 采购单ID
     * @return 采购单明细集合
     */
    List<PurchaseItem> selectPurchaseItemListByPurchaseId(Long purchaseId);

    /**
     * 根据采购单ID删除明细
     *
     * @param purchaseId 采购单ID
     * @return 结果
     */
    int deletePurchaseItemByPurchaseId(Long purchaseId);

    /**
     * 批量新增采购单明细
     *
     * @param items 采购单明细集合
     * @return 结果
     */
    int insertPurchaseItemBatch(List<PurchaseItem> items);

    /**
     * 按配送日期汇总已确认订单明细（join t_sale_order 过滤 status=1 且 is_deleted=0），按 sku_id 合并数量
     *
     * @param deliveryDate 配送日期
     * @return 汇总后的采购明细（quantity=合计数量，unit_price=商品单价，product_name/unit/spec 取首条明细）
     */
    List<PurchaseItem> selectSummaryByDeliveryDate(LocalDate deliveryDate);

    /**
     * 按指定订单ID集合汇总已确认订单明细（join t_sale_order 过滤 status=1 且 is_deleted=0），按 sku_id 合并数量
     *
     * @param orderIds 销售订单ID集合
     * @return 汇总后的采购明细（quantity=合计数量，unit_price=商品单价，product_name/unit/spec 取首条明细）
     */
    List<PurchaseItem> selectSummaryByOrderIds(java.util.Collection<Long> orderIds);
}

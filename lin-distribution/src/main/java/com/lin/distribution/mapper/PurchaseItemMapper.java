package com.lin.distribution.mapper;

import com.lin.distribution.domain.PurchaseItem;
import com.lin.distribution.vo.PurchaseDaySummaryVO;

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
     * 修改批次可编辑字段（数量/进货价/小计/供应商/备注，允许显式置空）
     *
     * @param purchaseItem 批次行（id 必填）
     * @return 结果
     */
    int updateBatchFields(PurchaseItem purchaseItem);

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
     * 按配送日期汇总当日应采清单（D-056）：订单明细（status>=1）按「sku+品名+规格+单位」聚合 SUM(num)，
     * 全为 0 的纯退货组不进清单。仅用于读取与批次录入校验，不落库。
     *
     * @param orderDate 配送日期（=采购日期）
     * @return 应采清单行（skuId/productName/productSpec/productUnit/requiredQty）
     */
    List<PurchaseDaySummaryVO.RequiredRow> selectRequiredSummary(LocalDate orderDate);
}

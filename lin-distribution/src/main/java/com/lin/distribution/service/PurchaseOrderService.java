package com.lin.distribution.service;

import com.lin.distribution.domain.PurchaseItem;
import com.lin.distribution.domain.PurchaseModifyLog;
import com.lin.distribution.domain.PurchaseOrder;
import com.lin.distribution.dto.PurchaseBatchDTO;
import com.lin.distribution.vo.PurchaseDaySummaryVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 采购单Service接口
 *
 * <p>D-056~D-063：采购单 = 当日订单应采清单（实时视图）+ 分批进货录入（行=批次），用于成本统计。</p>
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
     * 查询采购单列表（含批次数/已采/应采聚合）
     *
     * @param purchaseOrder 采购单
     * @return 采购单集合
     */
    List<PurchaseOrder> selectPurchaseOrderList(PurchaseOrder purchaseOrder);

    /**
     * 查询采购单明细（批次）列表
     *
     * @param purchaseId 采购单ID
     * @return 采购明细集合
     */
    List<PurchaseItem> selectPurchaseItemListByPurchaseId(Long purchaseId);

    /**
     * 当日应采汇总（D-056/D-059）：订单明细实时视图（应采）∪ 已录批次，
     * 每行汇总 应采/已采/待采/批次数/加权均价/金额 + 批次明细；采购单未建时仅返回应采行。
     *
     * @param orderDate 采购日期（=配送日期）
     * @return 日应采汇总
     */
    PurchaseDaySummaryVO daySummary(LocalDate orderDate);

    /**
     * 取或惰性创建当日采购单（D-056：一天一单，首次录入批次时创建）
     *
     * @param orderDate 采购日期（=配送日期）
     * @return 采购单（含批次明细）
     */
    PurchaseOrder getOrCreateDayPurchase(LocalDate orderDate);

    /**
     * 追加一个进货批次（D-057/D-058）：商品必须命中当日应采清单，
     * 品名/规格/单位/应采数量由后端快照回填。
     *
     * @param purchaseId 采购单ID
     * @param dto        批次录入请求
     * @return 新增的批次行
     */
    PurchaseItem addBatch(Long purchaseId, PurchaseBatchDTO dto);

    /**
     * 批量追加进货批次（任一行非法整体回滚）
     *
     * @param purchaseId 采购单ID
     * @param items      批次录入请求集合
     * @return 成功录入行数
     */
    int addBatchBulk(Long purchaseId, List<PurchaseBatchDTO> items);

    /**
     * 修改批次（仅草稿）：可改数量/进货价/供应商/备注，商品键锁定
     *
     * @param purchaseId 采购单ID
     * @param itemId     批次行ID
     * @param dto        批次录入请求
     * @return 结果
     */
    int updateBatch(Long purchaseId, Long itemId, PurchaseBatchDTO dto);

    /**
     * 删除批次（仅草稿）
     *
     * @param purchaseId 采购单ID
     * @param itemId     批次行ID
     * @return 结果
     */
    int deleteBatch(Long purchaseId, Long itemId);

    /**
     * 修改采购单单头默认供应商/采购员/备注（明细走批次接口）
     *
     * @param purchaseOrder 采购单（含 id）
     * @return 结果
     */
    int updatePurchaseHeader(PurchaseOrder purchaseOrder);

    /**
     * 确认采购单（草稿→已确认，草稿期录入完成、锁定批次增删）
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

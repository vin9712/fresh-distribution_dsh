package com.lin.distribution.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.PurchaseOrderStatus;
import com.lin.distribution.domain.PurchaseItem;
import com.lin.distribution.domain.PurchaseModifyLog;
import com.lin.distribution.domain.PurchaseOrder;
import com.lin.distribution.dto.PurchaseBatchDTO;
import com.lin.distribution.mapper.MonthSettlementMapper;
import com.lin.distribution.mapper.PurchaseItemMapper;
import com.lin.distribution.mapper.PurchaseModifyLogMapper;
import com.lin.distribution.mapper.PurchaseOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.PurchaseOrderService;
import com.lin.distribution.vo.PurchaseDaySummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.dao.DuplicateKeyException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 采购单Service业务层处理
 *
 * <p>D-056~D-063 重设计：采购单主体 = 采购日期（=配送日期，一天一单）；
 * 「应采清单」为订单明细实时视图（不落库）；{@code purchase_item} 行 = 一次实际进货批次，
 * 支持同一商品多批次（不同进货价/供应商），成本按批次加权平均。</p>
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {
    /** 加权均价保留位数 */
    private static final int AVG_PRICE_SCALE = 4;
    /** 金额保留位数 */
    private static final int AMOUNT_SCALE = 2;

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseItemMapper purchaseItemMapper;
    private final PurchaseModifyLogMapper purchaseModifyLogMapper;
    private final MonthSettlementMapper monthSettlementMapper;
    private final BizCodeService bizCodeService;

    @Override
    public PurchaseOrder selectPurchaseOrderById(Long id) {
        return purchaseOrderMapper.selectPurchaseOrderById(id);
    }

    @Override
    public List<PurchaseOrder> selectPurchaseOrderList(PurchaseOrder purchaseOrder) {
        return purchaseOrderMapper.selectPurchaseOrderList(purchaseOrder);
    }

    @Override
    public List<PurchaseItem> selectPurchaseItemListByPurchaseId(Long purchaseId) {
        return purchaseItemMapper.selectPurchaseItemListByPurchaseId(purchaseId);
    }

    // ==================== 日应采汇总（D-056/D-059） ====================

    @Override
    public PurchaseDaySummaryVO daySummary(LocalDate orderDate) {
        if (orderDate == null) {
            throw new ServiceException("采购日期不能为空");
        }
        List<PurchaseDaySummaryVO.RequiredRow> required = purchaseItemMapper.selectRequiredSummary(orderDate);
        PurchaseOrder purchase = purchaseOrderMapper.selectActiveByOrderDate(orderDate);
        List<PurchaseItem> items = purchase == null
                ? new ArrayList<>()
                : purchaseItemMapper.selectPurchaseItemListByPurchaseId(purchase.getId());

        // 行 = 应采清单 ∪ 已录批次（按采购汇总键合并；应采行在前，订单已撤回的遗留批次追加在后）
        Map<String, PurchaseDaySummaryVO.Row> rowMap = new LinkedHashMap<>();
        for (PurchaseDaySummaryVO.RequiredRow r : required) {
            String key = summaryKey(r.getSkuId(), r.getProductName(), r.getProductSpec(), r.getProductUnit());
            rowMap.put(key, newRow(key, r.getSkuId(), r.getProductName(), r.getProductSpec(), r.getProductUnit(),
                    nvl(r.getRequiredQty()), false));
        }
        for (PurchaseItem item : items) {
            String key = summaryKey(item.getSkuId(), item.getProductName(), item.getProductSpec(), item.getProductUnit());
            PurchaseDaySummaryVO.Row row = rowMap.get(key);
            if (row == null) {
                // 订单已撤回但批次仍在（成本是事实，不自动删）
                row = newRow(key, item.getSkuId(), item.getProductName(), item.getProductSpec(), item.getProductUnit(),
                        BigDecimal.ZERO, true);
                rowMap.put(key, row);
            }
            row.setPurchasedQty(row.getPurchasedQty().add(nvl(item.getQuantity())));
            row.setAmount(row.getAmount().add(nvl(item.getSubtotal())));
            row.setBatchCount(row.getBatchCount() + 1);
            row.getBatches().add(toBatch(item));
        }

        List<PurchaseDaySummaryVO.Row> rows = new ArrayList<>(rowMap.values());
        BigDecimal requiredTotal = BigDecimal.ZERO;
        BigDecimal purchasedTotal = BigDecimal.ZERO;
        BigDecimal pendingTotal = BigDecimal.ZERO;
        int purchasedItemCount = 0;
        int overCount = 0;
        for (PurchaseDaySummaryVO.Row row : rows) {
            BigDecimal pending = row.getRequiredQty().subtract(row.getPurchasedQty());
            row.setPendingQty(pending);
            row.setAvgPrice(row.getPurchasedQty().compareTo(BigDecimal.ZERO) > 0
                    ? row.getAmount().divide(row.getPurchasedQty(), AVG_PRICE_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            if (!Boolean.TRUE.equals(row.getOrphan())
                    && row.getPurchasedQty().compareTo(BigDecimal.ZERO) > 0) {
                purchasedItemCount++;
            }
            if (pending.compareTo(BigDecimal.ZERO) < 0) {
                overCount++;
            }
            requiredTotal = requiredTotal.add(row.getRequiredQty());
            purchasedTotal = purchasedTotal.add(row.getPurchasedQty());
            pendingTotal = pendingTotal.add(pending);
        }

        return PurchaseDaySummaryVO.builder()
                .purchaseId(purchase == null ? null : purchase.getId())
                .code(purchase == null ? null : purchase.getCode())
                .status(purchase == null ? null : purchase.getStatus())
                .orderDate(orderDate)
                .supplierId(purchase == null ? null : purchase.getSupplierId())
                .supplierName(purchase == null ? null : purchase.getSupplierName())
                .purchaser(purchase == null ? null : purchase.getPurchaser())
                .remark(purchase == null ? null : purchase.getRemark())
                .totalAmount(purchase == null ? BigDecimal.ZERO : nvl(purchase.getTotalAmount()))
                .requiredItemCount(required.size())
                .purchasedItemCount(purchasedItemCount)
                .requiredQty(requiredTotal)
                .purchasedQty(purchasedTotal)
                .pendingQty(pendingTotal)
                .overCount(overCount)
                .rows(rows)
                .build();
    }

    /**
     * 取或惰性创建当日采购单（D-056：一天一单，首次录入批次时创建）。
     *
     * <p>并发防重：依赖 {@code uk_purchase_order_active_date} 唯一索引（active_date 生成列，
     * 作废单为 NULL 不占位）。并发下后到者的 insert 撞唯一键，捕获后回查先建者返回；
     * 注意本方法刻意不开事务——回查需要新的读快照才能看到并发事务已提交的现单。</p>
     */
    @Override
    public PurchaseOrder getOrCreateDayPurchase(LocalDate orderDate) {
        if (orderDate == null) {
            throw new ServiceException("采购日期不能为空");
        }
        PurchaseOrder existing = purchaseOrderMapper.selectActiveByOrderDate(orderDate);
        if (existing != null) {
            existing.setItems(purchaseItemMapper.selectPurchaseItemListByPurchaseId(existing.getId()));
            return existing;
        }
        PurchaseOrder order = new PurchaseOrder();
        order.setCode(bizCodeService.nextDailyCode("purchase", "PC", 3));
        order.setOrderDate(orderDate);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setStatus(PurchaseOrderStatus.DRAFT.getCode());
        order.setCreateTime(DateUtils.getNowDate());
        try {
            purchaseOrderMapper.insertPurchaseOrder(order);
        } catch (DuplicateKeyException e) {
            // 并发建单撞唯一键：以先提交者为准，回查现单（无外层事务时为新快照，可见对方已提交行）
            PurchaseOrder winner = purchaseOrderMapper.selectActiveByOrderDate(orderDate);
            if (winner == null) {
                throw new ServiceException("当日采购单创建冲突，请刷新后重试");
            }
            order = winner;
        }
        order.setItems(new ArrayList<>());
        log.info("[purchase day init] 创建当日采购单 {}（采购日期 {}，操作者：{}）",
                order.getCode(), orderDate, resolveOperator());
        return order;
    }

    // ==================== 批次录入（D-057/D-058） ====================

    @Override
    @Transactional
    public PurchaseItem addBatch(Long purchaseId, PurchaseBatchDTO dto) {
        PurchaseOrder purchase = getDraftPurchase(purchaseId);
        Map<String, PurchaseDaySummaryVO.RequiredRow> required = requiredMap(purchase.getOrderDate());
        List<PurchaseItem> existing = purchaseItemMapper.selectPurchaseItemListByPurchaseId(purchaseId);
        PurchaseItem item = doAddBatch(purchase, required, existing, dto);
        recalcTotal(purchaseId);
        return item;
    }

    @Override
    @Transactional
    public int addBatchBulk(Long purchaseId, List<PurchaseBatchDTO> items) {
        if (CollectionUtils.isEmpty(items)) {
            throw new ServiceException("请至少录入一个采购批次");
        }
        PurchaseOrder purchase = getDraftPurchase(purchaseId);
        Map<String, PurchaseDaySummaryVO.RequiredRow> required = requiredMap(purchase.getOrderDate());
        // 先全量校验（商品必须在当日应采清单 + 数量/单价合法），保证任一行非法整体不落库
        for (PurchaseBatchDTO dto : items) {
            resolveRequiredRow(dto, required);
        }
        List<PurchaseItem> existing = purchaseItemMapper.selectPurchaseItemListByPurchaseId(purchaseId);
        for (PurchaseBatchDTO dto : items) {
            doAddBatch(purchase, required, existing, dto);
        }
        recalcTotal(purchaseId);
        return items.size();
    }

    @Override
    @Transactional
    public int updateBatch(Long purchaseId, Long itemId, PurchaseBatchDTO dto) {
        getDraftPurchase(purchaseId);
        PurchaseItem item = purchaseItemMapper.selectPurchaseItemById(itemId);
        if (item == null || !Objects.equals(item.getPurchaseId(), purchaseId)) {
            throw new ServiceException("采购批次不存在或不属于该采购单");
        }
        validateBatchFields(dto);
        PurchaseItem update = new PurchaseItem();
        update.setId(itemId);
        update.setQuantity(dto.getQuantity());
        update.setUnitPrice(dto.getUnitPrice());
        update.setSubtotal(calcSubtotal(dto.getQuantity(), dto.getUnitPrice()));
        // updateBatchFields 是全字段 SET：dto 未传 supplierId 时沿用原值，避免抹掉批次已有供应商
        update.setSupplierId(dto.getSupplierId() != null ? dto.getSupplierId() : item.getSupplierId());
        update.setSupplierName(StringUtils.trimToNull(dto.getSupplierName()));
        update.setRemark(StringUtils.trimToNull(dto.getRemark()));
        purchaseItemMapper.updateBatchFields(update);
        recalcTotal(purchaseId);
        return 1;
    }

    @Override
    @Transactional
    public int deleteBatch(Long purchaseId, Long itemId) {
        getDraftPurchase(purchaseId);
        PurchaseItem item = purchaseItemMapper.selectPurchaseItemById(itemId);
        if (item == null || !Objects.equals(item.getPurchaseId(), purchaseId)) {
            throw new ServiceException("采购批次不存在或不属于该采购单");
        }
        int rows = purchaseItemMapper.deletePurchaseItemByIds(new Long[]{itemId});
        recalcTotal(purchaseId);
        return rows;
    }

    /**
     * 单批次落库（调用方保证 required/existing 已加载，供批量录入复用计数器）
     */
    private PurchaseItem doAddBatch(PurchaseOrder purchase,
                                    Map<String, PurchaseDaySummaryVO.RequiredRow> required,
                                    List<PurchaseItem> existing,
                                    PurchaseBatchDTO dto) {
        PurchaseDaySummaryVO.RequiredRow requiredRow = resolveRequiredRow(dto, required);
        String key = summaryKey(dto.getSkuId(), dto.getProductName(), dto.getProductSpec(), dto.getProductUnit());
        PurchaseItem item = new PurchaseItem();
        item.setPurchaseId(purchase.getId());
        item.setBatchNo(nextBatchNo(existing, key));
        // 快照一律取应采清单（忽略前端传值），保证成本与订单口径一致
        item.setSkuId(requiredRow.getSkuId());
        item.setProductName(requiredRow.getProductName());
        item.setProductSpec(requiredRow.getProductSpec());
        item.setProductUnit(requiredRow.getProductUnit());
        item.setQuantity(dto.getQuantity());
        item.setRequiredQty(nvl(requiredRow.getRequiredQty()));
        item.setUnitPrice(dto.getUnitPrice());
        item.setSubtotal(calcSubtotal(dto.getQuantity(), dto.getUnitPrice()));
        item.setSort(nextSort(existing));
        item.setSupplierId(dto.getSupplierId());
        item.setSupplierName(StringUtils.trimToNull(dto.getSupplierName()));
        item.setRemark(StringUtils.trimToNull(dto.getRemark()));
        item.setCreateBy(resolveOperator());
        item.setCreateTime(DateUtils.getNowDate());
        purchaseItemMapper.insertPurchaseItem(item);
        existing.add(item);
        return item;
    }

    @Override
    @Transactional
    public int updatePurchaseHeader(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null || purchaseOrder.getId() == null) {
            throw new ServiceException("采购单ID不能为空");
        }
        PurchaseOrder exist = getExistPurchaseOrder(purchaseOrder.getId());
        if (!PurchaseOrderStatus.DRAFT.getCode().equals(exist.getStatus())
                && !PurchaseOrderStatus.CONFIRMED.getCode().equals(exist.getStatus())) {
            throw new ServiceException("仅草稿/已确认采购单可修改单头信息");
        }
        PurchaseOrder update = new PurchaseOrder();
        update.setId(exist.getId());
        update.setSupplierId(purchaseOrder.getSupplierId());
        update.setSupplierName(StringUtils.trimToNull(purchaseOrder.getSupplierName()));
        update.setPurchaser(StringUtils.trimToNull(purchaseOrder.getPurchaser()));
        update.setRemark(StringUtils.trimToNull(purchaseOrder.getRemark()));
        update.setUpdateBy(resolveOperator());
        update.setUpdateTime(DateUtils.getNowDate());
        return purchaseOrderMapper.updatePurchaseHeader(update);
    }

    // ==================== 状态流转（D-060） ====================

    @Override
    @Transactional
    public int confirm(Long id) {
        PurchaseOrder exist = getExistPurchaseOrder(id);
        if (!PurchaseOrderStatus.DRAFT.getCode().equals(exist.getStatus())) {
            throw new ServiceException("仅草稿状态可确认");
        }
        if (CollectionUtils.isEmpty(purchaseItemMapper.selectPurchaseItemListByPurchaseId(id))) {
            throw new ServiceException("当日采购单尚未录入任何批次，不能确认");
        }
        PurchaseOrder update = new PurchaseOrder();
        update.setId(id);
        update.setStatus(PurchaseOrderStatus.CONFIRMED.getCode());
        update.setUpdateTime(DateUtils.getNowDate());
        return purchaseOrderMapper.updatePurchaseOrder(update);
    }

    @Override
    @Transactional
    public int stockIn(Long id) {
        PurchaseOrder exist = getExistPurchaseOrder(id);
        if (!PurchaseOrderStatus.CONFIRMED.getCode().equals(exist.getStatus())) {
            throw new ServiceException("仅已确认状态可入库");
        }
        PurchaseOrder update = new PurchaseOrder();
        update.setId(id);
        update.setStatus(PurchaseOrderStatus.STOCKED.getCode());
        update.setUpdateTime(DateUtils.getNowDate());
        return purchaseOrderMapper.updatePurchaseOrder(update);
    }

    /**
     * 批量入库（S2-2.2 批量确认成本）：逐单校验仅已确认可入库，任一不合法整体回滚；
     * 蓝图「到货→待确认成本→已确认成本」状态提示对应批量场景的成本收口动作。
     */
    @Override
    @Transactional
    public int batchStockIn(Long[] ids) {
        if (ids == null || ids.length == 0) {
            throw new ServiceException("请选择需要入库的采购单");
        }
        int rows = 0;
        for (Long id : ids) {
            PurchaseOrder exist = getExistPurchaseOrder(id);
            if (!PurchaseOrderStatus.CONFIRMED.getCode().equals(exist.getStatus())) {
                throw new ServiceException("采购单 " + exist.getCode() + " 非已确认状态，不可入库");
            }
            PurchaseOrder update = new PurchaseOrder();
            update.setId(id);
            update.setStatus(PurchaseOrderStatus.STOCKED.getCode());
            update.setUpdateTime(DateUtils.getNowDate());
            rows += purchaseOrderMapper.updatePurchaseOrder(update);
        }
        log.info("[purchase batch stock-in] 批量入库 {} 单（操作者：{}）", rows, resolveOperator());
        return rows;
    }

    /**
     * 供应商补录（S2-2.2）：草稿/已确认采购单补录或修正供应商与采购员（蓝图「供应商补录」）；
     * 已入库后成本已确认，供应商不再变更；至少提供供应商/采购员其一。
     */
    @Override
    @Transactional
    public int backfillSupplier(Long id, Long supplierId, String supplierName, String purchaser) {
        PurchaseOrder exist = getExistPurchaseOrder(id);
        if (!PurchaseOrderStatus.DRAFT.getCode().equals(exist.getStatus())
                && !PurchaseOrderStatus.CONFIRMED.getCode().equals(exist.getStatus())) {
            throw new ServiceException("仅草稿/已确认采购单可补录供应商");
        }
        if (supplierId == null && StringUtils.isBlank(supplierName) && StringUtils.isBlank(purchaser)) {
            throw new ServiceException("供应商与采购员至少填写其一");
        }
        PurchaseOrder update = new PurchaseOrder();
        update.setId(id);
        if (supplierId != null) {
            update.setSupplierId(supplierId);
        }
        if (StringUtils.isNotBlank(supplierName)) {
            update.setSupplierName(supplierName.trim());
        }
        if (StringUtils.isNotBlank(purchaser)) {
            update.setPurchaser(purchaser.trim());
        }
        update.setUpdateTime(DateUtils.getNowDate());
        int rows = purchaseOrderMapper.updatePurchaseOrder(update);
        log.info("[purchase supplier backfill] 采购单 {} 供应商补录：{} / {}（操作者：{}）",
                exist.getCode(), supplierId != null ? supplierId : exist.getSupplierId(),
                supplierName, resolveOperator());
        return rows;
    }

    /**
     * 已确认采购单直接调整数量/成本（蓝图 W0-2.5「已确认采购纠错」）：
     * 仅允许对已有明细行修改数量/采购单价（禁止增删行、禁止改快照标识字段），重算小计与总额，
     * 并记录调整前后金额与明细快照到 t_purchase_modify_log（操作日志+前后金额记录）。
     * 调整后报表/经营概览按实时采购数据重算（W0-3.3 经营概览读实时表）。
     */
    @Override
    @Transactional
    public PurchaseOrder adjustConfirmedPurchase(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null || purchaseOrder.getId() == null) {
            throw new ServiceException("采购单ID不能为空");
        }
        PurchaseOrder exist = getExistPurchaseOrder(purchaseOrder.getId());
        if (!PurchaseOrderStatus.CONFIRMED.getCode().equals(exist.getStatus())) {
            throw new ServiceException("仅已确认采购单可直接调整数量/成本，请先确认采购单");
        }
        // 月结冻结校验（W0-3.1）：采购单无 customer_id，按归属月已月结即锁定（供应商池化口径）
        if (exist.getOrderDate() != null) {
            String month = exist.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            if (monthSettlementMapper.existsByMonth(month) > 0) {
                throw new ServiceException("该月（" + month + "）已月结，采购成本已冻结，请使用下月调整单");
            }
        }
        List<PurchaseItem> newItems = purchaseOrder.getItems();
        if (CollectionUtils.isEmpty(newItems)) {
            throw new ServiceException("采购明细不能为空");
        }

        // 调整前快照：既有明细 + 总额
        List<PurchaseItem> beforeItems = purchaseItemMapper.selectPurchaseItemListByPurchaseId(exist.getId());
        BigDecimal beforeTotal = nvl(exist.getTotalAmount());
        Map<Long, PurchaseItem> beforeMap = beforeItems.stream()
                .collect(Collectors.toMap(PurchaseItem::getId, p -> p, (a, b) -> a));

        // 定位校验：调整行必须为已有明细（禁止增删），数量/单价合法
        for (PurchaseItem item : newItems) {
            if (item.getId() == null || !beforeMap.containsKey(item.getId())) {
                throw new ServiceException("调整明细必须为采购单已有明细行（W0-2.5 仅改数量/成本，禁止增删行）");
            }
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("采购数量不能为空且不能为负");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("采购单价不能为空且不能为负");
            }
        }

        // 重算小计与总额；锁定快照标识字段（sku/品名/规格/单位 不可变更，仅数量成本可变）
        BigDecimal afterTotal = BigDecimal.ZERO;
        int sort = 0;
        for (PurchaseItem item : newItems) {
            PurchaseItem before = beforeMap.get(item.getId());
            item.setSkuId(before.getSkuId());
            item.setProductName(before.getProductName());
            item.setProductSpec(before.getProductSpec());
            item.setProductUnit(before.getProductUnit());
            item.setSubtotal(calcSubtotal(item.getQuantity(), item.getUnitPrice()));
            item.setSort(sort++);
            afterTotal = afterTotal.add(item.getSubtotal());
        }

        // 更新明细（保留主键逐行更新）+ 重算总额
        for (PurchaseItem item : newItems) {
            PurchaseItem update = new PurchaseItem();
            update.setId(item.getId());
            update.setQuantity(item.getQuantity());
            update.setUnitPrice(item.getUnitPrice());
            update.setSubtotal(item.getSubtotal());
            purchaseItemMapper.updatePurchaseItem(update);
        }
        PurchaseOrder header = new PurchaseOrder();
        header.setId(exist.getId());
        header.setTotalAmount(afterTotal);
        header.setUpdateTime(DateUtils.getNowDate());
        purchaseOrderMapper.updatePurchaseOrder(header);

        // 写调整审计（前后金额 + 明细快照）
        PurchaseModifyLog modifyLog = new PurchaseModifyLog();
        modifyLog.setPurchaseId(exist.getId());
        modifyLog.setPurchaseCode(exist.getCode());
        modifyLog.setBeforeAmount(beforeTotal);
        modifyLog.setAfterAmount(afterTotal);
        modifyLog.setBeforeItems(JSON.toJSONString(toItemSnapshot(beforeItems)));
        modifyLog.setAfterItems(JSON.toJSONString(toItemSnapshot(newItems)));
        modifyLog.setOperator(resolveOperator());
        modifyLog.setOperateTime(DateUtils.getNowDate());
        modifyLog.setRemark(StringUtils.trimToNull(purchaseOrder.getRemark()));
        purchaseModifyLogMapper.insertPurchaseModifyLog(modifyLog);

        log.info("[purchase adjust] 采购单 {} 调整：总额 {} → {}，差异 {}",
                exist.getCode(), beforeTotal, afterTotal, afterTotal.subtract(beforeTotal));

        exist.setTotalAmount(afterTotal);
        exist.setItems(newItems);
        return exist;
    }

    @Override
    public List<PurchaseModifyLog> selectModifyLogsByPurchaseId(Long purchaseId) {
        return purchaseModifyLogMapper.selectListByPurchaseId(purchaseId);
    }

    @Override
    @Transactional
    public int deletePurchaseOrderByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (Long id : ids) {
            PurchaseOrder exist = purchaseOrderMapper.selectPurchaseOrderById(id);
            if (exist == null) {
                continue;
            }
            if (!PurchaseOrderStatus.DRAFT.getCode().equals(exist.getStatus())) {
                throw new ServiceException("仅草稿状态可删除");
            }
            purchaseItemMapper.deletePurchaseItemByPurchaseId(id);
        }
        return purchaseOrderMapper.deletePurchaseOrderByIds(ids);
    }

    // ==================== 内部工具 ====================

    /**
     * 当日应采清单（按采购汇总键索引），用于批次录入的商品命中校验
     */
    private Map<String, PurchaseDaySummaryVO.RequiredRow> requiredMap(LocalDate orderDate) {
        Map<String, PurchaseDaySummaryVO.RequiredRow> map = new HashMap<>();
        for (PurchaseDaySummaryVO.RequiredRow row : purchaseItemMapper.selectRequiredSummary(orderDate)) {
            map.put(summaryKey(row.getSkuId(), row.getProductName(), row.getProductSpec(), row.getProductUnit()), row);
        }
        return map;
    }

    /**
     * 重算并回写采购单总额 = Σ批次小计
     */
    private void recalcTotal(Long purchaseId) {
        List<PurchaseItem> items = purchaseItemMapper.selectPurchaseItemListByPurchaseId(purchaseId);
        BigDecimal total = items.stream()
                .map(it -> nvl(it.getSubtotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PurchaseOrder header = new PurchaseOrder();
        header.setId(purchaseId);
        header.setTotalAmount(total);
        header.setUpdateTime(DateUtils.getNowDate());
        purchaseOrderMapper.updatePurchaseOrder(header);
    }

    private PurchaseOrder getDraftPurchase(Long purchaseId) {
        PurchaseOrder exist = getExistPurchaseOrder(purchaseId);
        if (!PurchaseOrderStatus.DRAFT.getCode().equals(exist.getStatus())) {
            throw new ServiceException("仅草稿状态可增删改采购批次（已确认请走「调整成本」）");
        }
        return exist;
    }

    private PurchaseOrder getExistPurchaseOrder(Long id) {
        if (id == null) {
            throw new ServiceException("采购单ID不能为空");
        }
        PurchaseOrder exist = purchaseOrderMapper.selectPurchaseOrderById(id);
        if (exist == null) {
            throw new ServiceException("采购单不存在");
        }
        return exist;
    }

    /**
     * 校验批次字段并解析命中的应采清单行（D-058：商品必须在当日订单商品内）
     */
    private PurchaseDaySummaryVO.RequiredRow resolveRequiredRow(PurchaseBatchDTO dto,
                                                               Map<String, PurchaseDaySummaryVO.RequiredRow> required) {
        validateBatchFields(dto);
        String key = summaryKey(dto.getSkuId(), dto.getProductName(), dto.getProductSpec(), dto.getProductUnit());
        PurchaseDaySummaryVO.RequiredRow row = required.get(key);
        if (row == null) {
            throw new ServiceException("商品【" + displayName(dto) + "】不在当日订单商品内，不可录入采购");
        }
        return row;
    }

    private void validateBatchFields(PurchaseBatchDTO dto) {
        if (dto == null) {
            throw new ServiceException("采购批次不能为空");
        }
        if (dto.getQuantity() == null || dto.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("采购数量必须大于 0");
        }
        if (dto.getUnitPrice() == null || dto.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("进货价不能为空且不能为负");
        }
    }

    private int nextBatchNo(List<PurchaseItem> existing, String key) {
        return existing.stream()
                .filter(it -> key.equals(summaryKey(it.getSkuId(), it.getProductName(), it.getProductSpec(), it.getProductUnit())))
                .map(it -> Optional.ofNullable(it.getBatchNo()).orElse(0))
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    private int nextSort(List<PurchaseItem> existing) {
        return existing.stream()
                .map(it -> Optional.ofNullable(it.getSort()).orElse(0))
                .max(Comparator.naturalOrder())
                .orElse(-1) + 1;
    }

    private BigDecimal calcSubtotal(BigDecimal quantity, BigDecimal unitPrice) {
        return NumberUtils.toScaledBigDecimal(quantity.multiply(unitPrice), AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private PurchaseDaySummaryVO.Row newRow(String key, Long skuId, String name, String spec, String unit,
                                            BigDecimal requiredQty, boolean orphan) {
        return PurchaseDaySummaryVO.Row.builder()
                .key(key)
                .skuId(skuId)
                .productName(name)
                .productSpec(spec)
                .productUnit(unit)
                .requiredQty(requiredQty)
                .purchasedQty(BigDecimal.ZERO)
                .pendingQty(BigDecimal.ZERO)
                .batchCount(0)
                .avgPrice(BigDecimal.ZERO)
                .amount(BigDecimal.ZERO)
                .orphan(orphan)
                .batches(new ArrayList<>())
                .build();
    }

    private PurchaseDaySummaryVO.Batch toBatch(PurchaseItem item) {
        return PurchaseDaySummaryVO.Batch.builder()
                .id(item.getId())
                .batchNo(item.getBatchNo())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .supplierId(item.getSupplierId())
                .supplierName(item.getSupplierName())
                .createBy(item.getCreateBy())
                .createTime(item.getCreateTime())
                .remark(item.getRemark())
                .build();
    }

    private String displayName(PurchaseBatchDTO dto) {
        return StringUtils.defaultIfBlank(dto.getProductName(),
                dto.getSkuId() == null ? "未知商品" : "SKU:" + dto.getSkuId());
    }

    /**
     * 采购汇总键：sku + 品名 + 规格 + 单位（与应采清单 group by、撤回级联扣除口径一致）
     */
    private String summaryKey(Long skuId, String productName, String productSpec, String productUnit) {
        return (skuId == null ? "_" : skuId)
                + "|" + StringUtils.defaultString(productName)
                + "|" + StringUtils.defaultString(productSpec)
                + "|" + StringUtils.defaultString(productUnit);
    }

    /**
     * 采购明细快照（JSON 审计用）：仅携带前后对比需要的字段，避免序列化冗余
     */
    private List<Map<String, Object>> toItemSnapshot(List<PurchaseItem> items) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PurchaseItem item : items) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", item.getId());
            m.put("skuId", item.getSkuId());
            m.put("productName", item.getProductName());
            m.put("productSpec", item.getProductSpec());
            m.put("productUnit", item.getProductUnit());
            m.put("quantity", item.getQuantity());
            m.put("unitPrice", item.getUnitPrice());
            m.put("subtotal", item.getSubtotal());
            list.add(m);
        }
        return list;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String resolveOperator() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}

package com.lin.distribution.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.PurchaseOrderStatus;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.DeliverySourceItem;
import com.lin.distribution.domain.PurchaseItem;
import com.lin.distribution.domain.PurchaseOrder;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.WithdrawCascadeResultVO;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliverySourceItemMapper;
import com.lin.distribution.mapper.PurchaseItemMapper;
import com.lin.distribution.mapper.PurchaseOrderMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.service.OrderWithdrawCascadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 订单撤回级联服务实现（W0-2.1，蓝图「撤回级联/共享单据撤回/空关联单据」）
 *
 * <p>核心口径：
 * <ul>
 *   <li>拒绝线：订单被已打印/已送达送货单占用，或被已入库（STOCKED）采购单引用 → 撤回拒绝
 *       （采购单无打印态，已入库视同已执行）；</li>
 *   <li>送货扣除：仅处理待打印（PENDING）单；软删该订单的 source_item 分配（唯一键含 is_deleted，
 *       释放后重新生成不撞键），有剩余分配的聚合行按「num=Σ剩余分配量、amount=price×num」重算，
 *       无剩余分配的行删除；整单无有效明细则作废（原因=订单撤回）；共享单保留、不影响其他订单；</li>
 *   <li>采购扣除：按采购汇总键（sku+品名+规格+单位，与 selectSummaryByOrderIds 聚合口径一致）
 *       扣减数量，小计=新数量×原单价，扣至 0/负（含单据被手工编辑后超扣）删除该行；
 *       source_order_ids 移除被撤订单并重算 total_amount；整单无明细则作废（原因=订单撤回）；</li>
 *   <li>事务边界：级联在调用方（updateSaleOrderStatus）的 @Transactional 内执行，
 *       校验先行（validateOrderWithdrawable 对全部待撤回订单无副作用预检），任一失败整体回滚。</li>
 * </ul></p>
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderWithdrawCascadeServiceImpl implements OrderWithdrawCascadeService {

    /** 空单自动作废的固定原因（蓝图「空关联单据」：记录原因“订单撤回”） */
    public static final String WITHDRAW_VOID_REASON = "订单撤回";

    /** 来源类型：自动生成（与 PurchaseOrderServiceImpl 口径一致） */
    private static final int SOURCE_TYPE_AUTO = 1;

    private final DeliverySourceItemMapper deliverySourceItemMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseItemMapper purchaseItemMapper;
    private final SaleOrderDetailMapper saleOrderDetailMapper;

    @Override
    public void validateOrderWithdrawable(SaleOrder saleOrder) {
        // 送货侧：被已打印/已送达送货单占用 → 拒绝（待打印单走级联扣除）
        List<String> blockedDeliveryCodes = findPrintedOrDeliveredDeliveryCodes(saleOrder.getId());
        if (!blockedDeliveryCodes.isEmpty()) {
            throw new ServiceException("订单已进入已打印/已送达送货单【"
                    + String.join("、", blockedDeliveryCodes) + "】，请先作废送货单后再撤回：" + saleOrder.getCode());
        }

        // 采购侧：被已入库采购单引用 → 拒绝（货物已到，扣除会破坏入库事实）
        List<PurchaseOrder> linkedPurchases = findLinkedAutoPurchases(saleOrder.getId());
        String stockedCodes = linkedPurchases.stream()
                .filter(po -> PurchaseOrderStatus.STOCKED.getCode().equals(po.getStatus()))
                .map(PurchaseOrder::getCode)
                .collect(Collectors.joining("、"));
        if (StringUtils.isNotBlank(stockedCodes)) {
            throw new ServiceException("订单已进入已入库采购单【" + stockedCodes + "】，不可撤回：" + saleOrder.getCode());
        }
    }

    @Override
    public WithdrawCascadeResultVO cascadeOnOrderWithdraw(Long saleOrderId) {
        WithdrawCascadeResultVO result = cascadeDeliveries(saleOrderId);
        result.merge(cascadePurchases(saleOrderId));
        log.info("[order withdraw cascade] 订单 {} 级联完成：作废送货单 {}，扣除送货单 {}，作废采购单 {}，扣除采购单 {}",
                saleOrderId, result.getVoidedDeliveryCodes(), result.getDeductedDeliveryCodes(),
                result.getVoidedPurchaseCodes(), result.getDeductedPurchaseCodes());
        return result;
    }

    // ==================== 送货级联 ====================

    /**
     * 送货级联：待打印单扣除/作废。前置校验已保证不含已打印/已送达单，此处对 PENDING 兜底断言。
     */
    private WithdrawCascadeResultVO cascadeDeliveries(Long saleOrderId) {
        WithdrawCascadeResultVO result = WithdrawCascadeResultVO.builder().build();
        List<DeliverySourceItem> allocations = deliverySourceItemMapper.selectValidBySaleOrderId(saleOrderId);
        if (CollectionUtils.isEmpty(allocations)) {
            return result;
        }
        List<Long> deliveryIds = allocations.stream()
                .map(DeliverySourceItem::getDeliveryId).distinct().collect(Collectors.toList());
        Map<Long, DeliveryOrder> deliveryMap = deliveryOrderMapper.selectListByIds(deliveryIds).stream()
                .collect(Collectors.toMap(DeliveryOrder::getId, d -> d, (a, b) -> a));

        for (Long deliveryId : deliveryIds) {
            DeliveryOrder delivery = deliveryMap.get(deliveryId);
            if (delivery == null) {
                continue;
            }
            if (!DeliveryOrderStatus.PENDING.getCode().equals(delivery.getStatus())) {
                // validateOrderWithdrawable 已拦截，防御性兜底：非待打印单不动、由调用方回滚
                throw new ServiceException("送货单【" + delivery.getCode() + "】不是待打印状态，订单撤回失败");
            }
            deductDelivery(delivery, saleOrderId, result);
        }
        return result;
    }

    /**
     * 单张送货单扣除：软删该订单分配 → 重算/删除聚合行 → 空单作废。
     */
    private void deductDelivery(DeliveryOrder delivery, Long saleOrderId, WithdrawCascadeResultVO result) {
        List<DeliverySourceItem> all = deliverySourceItemMapper.selectListByDeliveryId(delivery.getId());
        List<DeliverySourceItem> remaining = all.stream()
                .filter(si -> !Objects.equals(si.getSaleOrderId(), saleOrderId))
                .collect(Collectors.toList());

        // 软删释放该订单的分配（旧行保留审计；唯一键含 is_deleted，重新生成不撞键）
        deliverySourceItemMapper.deleteByDeliveryIdAndSaleOrderId(delivery.getId(), saleOrderId);

        if (remaining.isEmpty()) {
            voidDeliveryForWithdraw(delivery);
            result.getVoidedDeliveryCodes().add(delivery.getCode());
            return;
        }
        recomputeDeliveryDetails(delivery.getId(), remaining);
        result.getDeductedDeliveryCodes().add(delivery.getCode());
    }

    /**
     * 扣除后重算聚合行：有剩余分配的行 num=Σ剩余分配量、amount=price×num；
     * 无剩余分配的行删除（与明细物理删除口径一致）。共享单其他订单的分配不受影响。
     */
    private void recomputeDeliveryDetails(Long deliveryId, List<DeliverySourceItem> remaining) {
        Map<Long, BigDecimal> remainByDetail = remaining.stream()
                .collect(Collectors.groupingBy(DeliverySourceItem::getDeliveryDetailId, LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, DeliverySourceItem::getAllocatedQuantity, BigDecimal::add)));

        List<DeliveryOrderDetail> details = deliveryOrderDetailMapper.selectListByDeliveryId(deliveryId);
        Date now = DateUtils.getNowDate();
        for (DeliveryOrderDetail detail : details) {
            BigDecimal remainNum = remainByDetail.get(detail.getId());
            if (remainNum == null) {
                deliveryOrderDetailMapper.deleteDeliveryOrderDetailById(detail.getId());
                continue;
            }
            BigDecimal oldNum = Optional.ofNullable(detail.getNum()).orElse(BigDecimal.ZERO);
            if (remainNum.compareTo(oldNum) != 0) {
                detail.setNum(remainNum);
                detail.setAmount(Optional.ofNullable(detail.getPrice()).orElse(BigDecimal.ZERO).multiply(remainNum));
                detail.setUpdateTime(now);
                deliveryOrderDetailMapper.updateDeliveryOrderDetail(detail);
            }
        }
    }

    /**
     * 空送货单自动作废（蓝图「空关联单据」）：置 VOIDED + 原因/人/时间，兜底清理残留分配。
     */
    private void voidDeliveryForWithdraw(DeliveryOrder delivery) {
        delivery.setStatus(DeliveryOrderStatus.VOIDED.getCode());
        delivery.setVoidReason(WITHDRAW_VOID_REASON);
        delivery.setVoidBy(resolveOperator());
        delivery.setVoidTime(DateUtils.getNowDate());
        delivery.setUpdateTime(DateUtils.getNowDate());
        deliveryOrderMapper.updateDeliveryOrder(delivery);
        deliverySourceItemMapper.deleteByDeliveryId(delivery.getId());
    }

    private List<String> findPrintedOrDeliveredDeliveryCodes(Long saleOrderId) {
        List<DeliverySourceItem> allocations = deliverySourceItemMapper.selectValidBySaleOrderId(saleOrderId);
        if (CollectionUtils.isEmpty(allocations)) {
            return List.of();
        }
        List<Long> deliveryIds = allocations.stream()
                .map(DeliverySourceItem::getDeliveryId).distinct().collect(Collectors.toList());
        return deliveryOrderMapper.selectListByIds(deliveryIds).stream()
                .filter(d -> DeliveryOrderStatus.PRINTED.getCode().equals(d.getStatus())
                        || DeliveryOrderStatus.DELIVERED.getCode().equals(d.getStatus()))
                .map(DeliveryOrder::getCode)
                .collect(Collectors.toList());
    }

    // ==================== 采购级联 ====================

    /**
     * 采购级联：未入库（草稿/已确认）自动采购单扣除/作废；已入库单已在预检拒绝，作废单跳过。
     */
    private WithdrawCascadeResultVO cascadePurchases(Long saleOrderId) {
        WithdrawCascadeResultVO result = WithdrawCascadeResultVO.builder().build();
        for (PurchaseOrder purchase : findLinkedAutoPurchases(saleOrderId)) {
            if (PurchaseOrderStatus.STOCKED.getCode().equals(purchase.getStatus())) {
                throw new ServiceException("订单已进入已入库采购单【" + purchase.getCode() + "】，不可撤回");
            }
            if (PurchaseOrderStatus.VOIDED.getCode().equals(purchase.getStatus())) {
                continue;
            }
            deductPurchase(purchase, saleOrderId, result);
        }
        return result;
    }

    /**
     * 单张采购单扣除：按汇总键扣数量 → 删零行 → 重算总额 → 移除来源订单 → 空单作废。
     */
    private void deductPurchase(PurchaseOrder purchase, Long saleOrderId, WithdrawCascadeResultVO result) {
        // 该订单的有效明细按采购汇总键聚合（口径与 selectSummaryByOrderIds 一致）
        List<SaleOrderDetail> rows = saleOrderDetailMapper.selectValidByOrderIdIn(List.of(saleOrderId));
        Map<String, BigDecimal> deductMap = rows.stream()
                .collect(Collectors.groupingBy(this::purchaseSummaryKey, LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO,
                                row -> Optional.ofNullable(row.getNum()).orElse(BigDecimal.ZERO), BigDecimal::add)));

        List<PurchaseItem> items = purchaseItemMapper.selectPurchaseItemListByPurchaseId(purchase.getId());
        List<PurchaseItem> keptItems = new ArrayList<>();
        for (PurchaseItem item : items) {
            BigDecimal deductQty = deductMap.getOrDefault(purchaseSummaryKey(item), BigDecimal.ZERO);
            BigDecimal newQty = Optional.ofNullable(item.getQuantity()).orElse(BigDecimal.ZERO).subtract(deductQty);
            if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                // 扣至 0/负（含单据被手工编辑后数量小于应扣量）→ 删除该行，钳到 0 不影响其他行
                purchaseItemMapper.deletePurchaseItemByIds(new Long[]{item.getId()});
                continue;
            }
            if (newQty.compareTo(item.getQuantity()) != 0) {
                item.setQuantity(newQty);
                item.setSubtotal(newQty.multiply(Optional.ofNullable(item.getUnitPrice()).orElse(BigDecimal.ZERO)));
                purchaseItemMapper.updatePurchaseItem(item);
            }
            keptItems.add(item);
        }

        // 移除来源订单引用（保留其他订单的追溯）
        List<Long> sourceIds = parseSourceOrderIds(purchase.getSourceOrderIds());
        sourceIds.remove(saleOrderId);
        purchase.setSourceOrderIds(sourceIds.isEmpty() ? null : JSON.toJSONString(sourceIds));

        if (keptItems.isEmpty()) {
            voidPurchaseForWithdraw(purchase);
            result.getVoidedPurchaseCodes().add(purchase.getCode());
            return;
        }
        purchase.setTotalAmount(keptItems.stream()
                .map(it -> Optional.ofNullable(it.getSubtotal()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        purchase.setUpdateTime(DateUtils.getNowDate());
        purchaseOrderMapper.updatePurchaseOrder(purchase);
        result.getDeductedPurchaseCodes().add(purchase.getCode());
    }

    /**
     * 空采购单自动作废（蓝图「空关联单据」）：置 VOIDED + 原因/人/时间。
     */
    private void voidPurchaseForWithdraw(PurchaseOrder purchase) {
        purchase.setStatus(PurchaseOrderStatus.VOIDED.getCode());
        purchase.setVoidReason(WITHDRAW_VOID_REASON);
        purchase.setVoidBy(resolveOperator());
        purchase.setVoidTime(DateUtils.getNowDate());
        purchase.setUpdateTime(DateUtils.getNowDate());
        purchaseOrderMapper.updatePurchaseOrder(purchase);
    }

    /**
     * 查询引用该订单的自动生成采购单（source_order_ids JSON 包含判定，
     * 与 PurchaseOrderServiceImpl.checkOrdersNotInAutoPurchase 同口径：小量级，Java 侧过滤）
     */
    private List<PurchaseOrder> findLinkedAutoPurchases(Long saleOrderId) {
        PurchaseOrder query = new PurchaseOrder();
        query.setSourceType(SOURCE_TYPE_AUTO);
        List<PurchaseOrder> autoPurchases = purchaseOrderMapper.selectPurchaseOrderList(query);
        return autoPurchases.stream()
                .filter(po -> parseSourceOrderIds(po.getSourceOrderIds()).contains(saleOrderId))
                .collect(Collectors.toList());
    }

    private List<Long> parseSourceOrderIds(String sourceOrderIds) {
        if (StringUtils.isBlank(sourceOrderIds)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(JSON.parseArray(sourceOrderIds, Long.class));
        } catch (Exception e) {
            // 历史脏数据：无法解析视作未引用，不阻断撤回
            log.warn("[order withdraw cascade] source_order_ids 解析失败：{}", sourceOrderIds, e);
            return new ArrayList<>();
        }
    }

    // ==================== 汇总键 ====================

    /**
     * 采购汇总键：sku + 品名 + 规格 + 单位（与 selectSummaryByOrderIds 的 group by 一致；
     * 临时商品 sku_id 空以占位符参与，防同名不同规格误扣）
     */
    private String purchaseSummaryKey(SaleOrderDetail row) {
        return summaryKey(row.getSkuId(), row.getProductName(), row.getProductSpec(), row.getProductUnit());
    }

    private String purchaseSummaryKey(PurchaseItem item) {
        return summaryKey(item.getSkuId(), item.getProductName(), item.getProductSpec(), item.getProductUnit());
    }

    private String summaryKey(Long skuId, String productName, String productSpec, String productUnit) {
        return (skuId == null ? "_" : skuId)
                + "|" + StringUtils.defaultString(productName)
                + "|" + StringUtils.defaultString(productSpec)
                + "|" + StringUtils.defaultString(productUnit);
    }

    /**
     * 操作人：无登录上下文（单测/系统调用）回落 system
     */
    private String resolveOperator() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}

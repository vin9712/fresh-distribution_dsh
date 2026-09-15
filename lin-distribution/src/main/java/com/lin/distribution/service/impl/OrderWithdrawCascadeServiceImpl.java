package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.PurchaseOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.DeliverySourceItem;
import com.lin.distribution.domain.PurchaseItem;
import com.lin.distribution.domain.PurchaseOrder;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.WithdrawCascadeResultVO;
import com.lin.distribution.mapper.AcceptanceMapper;
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
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 *   <li>采购扣除（D-061）：按 order_date 反查当日非作废采购单，按采购汇总键（sku+品名+规格+单位）
 *       在批次维度扣减——按批次 id 倒序（后录入先扣），扣至 0/负删除该行，重算 total_amount；
 *       整单无明细则作废（原因=订单撤回）；</li>
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

    private final DeliverySourceItemMapper deliverySourceItemMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseItemMapper purchaseItemMapper;
    private final SaleOrderDetailMapper saleOrderDetailMapper;
    private final AcceptanceMapper acceptanceMapper;

    @Override
    public void validateOrderWithdrawable(SaleOrder saleOrder) {
        // 验收侧：已生成验收单（草稿/已提交）→ 拒绝。撤回后订单回到草稿但验收行仍挂在原明细上，
        // 会出现“已撤回却改不了单”的死循环；正确顺序是先在验收页删除/撤销验收单。
        Acceptance acceptance = acceptanceMapper.selectBySaleOrder(saleOrder.getId());
        if (acceptance != null) {
            throw new ServiceException("订单已生成验收单【" + acceptance.getCode()
                    + "】，请先撤销或删除验收单后再撤回：" + saleOrder.getCode());
        }

        // 送货侧：被已打印/已送达送货单占用 → 拒绝（待打印单走级联扣除）
        List<String> blockedDeliveryCodes = findPrintedOrDeliveredDeliveryCodes(saleOrder.getId());
        if (!blockedDeliveryCodes.isEmpty()) {
            throw new ServiceException("订单已进入已打印/已送达送货单【"
                    + String.join("、", blockedDeliveryCodes) + "】，请先作废送货单后再撤回：" + saleOrder.getCode());
        }

        // 送货侧（2026-09-14 补）：仅靠历史关联（pre-S14：t_delivery_order_detail.order_id）引用、
        // 且无 source_item 台账覆盖的送货单无法自动扣除/作废——放任撤回会让订单与送货单永久不一致
        // （订单回草稿可改，送货单仍列着它）
        List<Long> legacyDeliveryIds = deliveryOrderDetailMapper.selectListByOrderIdIn(List.of(saleOrder.getId()))
                .stream().map(DeliveryOrderDetail::getDeliveryId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        if (!legacyDeliveryIds.isEmpty()) {
            Set<Long> allocatedIds = deliverySourceItemMapper.selectValidBySaleOrderId(saleOrder.getId()).stream()
                    .map(DeliverySourceItem::getDeliveryId).collect(Collectors.toSet());
            String unwindableCodes = deliveryOrderMapper.selectListByIds(legacyDeliveryIds).stream()
                    .filter(d -> !Boolean.TRUE.equals(d.getIsDeleted()))
                    .filter(d -> !DeliveryOrderStatus.VOIDED.getCode().equals(d.getStatus()))
                    .filter(d -> !allocatedIds.contains(d.getId()))
                    .map(DeliveryOrder::getCode)
                    .collect(Collectors.joining("、"));
            if (StringUtils.isNotBlank(unwindableCodes)) {
                throw new ServiceException("订单已进入送货单【" + unwindableCodes
                        + "】（历史关联，系统无法自动扣除），请先作废该送货单后再撤回：" + saleOrder.getCode());
            }
        }

        // 采购侧：被已入库采购单引用 → 拒绝（货物已到，扣除会破坏入库事实）
        PurchaseOrder linkedPurchase = findDayPurchase(saleOrder);
        if (linkedPurchase != null && PurchaseOrderStatus.STOCKED.getCode().equals(linkedPurchase.getStatus())) {
            throw new ServiceException("订单已进入已入库采购单【" + linkedPurchase.getCode()
                    + "】，不可撤回：" + saleOrder.getCode());
        }
    }

    @Override
    public WithdrawCascadeResultVO cascadeOnOrderWithdraw(SaleOrder saleOrder) {
        WithdrawCascadeResultVO result = cascadeDeliveries(saleOrder.getId());
        result.merge(cascadePurchases(saleOrder));
        log.info("[order withdraw cascade] 订单 {} 级联完成：作废送货单 {}，扣除送货单 {}，作废采购单 {}，扣除采购单 {}",
                saleOrder.getCode(), result.getVoidedDeliveryCodes(), result.getDeductedDeliveryCodes(),
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
     * 采购级联（D-061）：当日采购单按批次扣减/作废；已入库单已在预检拒绝，作废单跳过。
     */
    private WithdrawCascadeResultVO cascadePurchases(SaleOrder saleOrder) {
        WithdrawCascadeResultVO result = WithdrawCascadeResultVO.builder().build();
        PurchaseOrder purchase = findDayPurchase(saleOrder);
        if (purchase == null) {
            return result;
        }
        if (PurchaseOrderStatus.STOCKED.getCode().equals(purchase.getStatus())) {
            throw new ServiceException("订单已进入已入库采购单【" + purchase.getCode() + "】，不可撤回");
        }
        if (PurchaseOrderStatus.VOIDED.getCode().equals(purchase.getStatus())) {
            return result;
        }
        deductPurchase(purchase, saleOrder.getId(), result);
        return result;
    }

    /**
     * 单张采购单扣除（D-061）：按汇总键在批次维度扣数量 → 删零行 → 重算总额 → 空单作废。
     */
    private void deductPurchase(PurchaseOrder purchase, Long saleOrderId, WithdrawCascadeResultVO result) {
        // 该订单的有效明细按采购汇总键聚合（口径与 selectSummaryByOrderIds 一致）
        List<SaleOrderDetail> rows = saleOrderDetailMapper.selectValidByOrderIdIn(List.of(saleOrderId));
        Map<String, BigDecimal> deductMap = rows.stream()
                .collect(Collectors.groupingBy(this::purchaseSummaryKey, LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO,
                                row -> Optional.ofNullable(row.getNum()).orElse(BigDecimal.ZERO), BigDecimal::add)));

        List<PurchaseItem> items = purchaseItemMapper.selectPurchaseItemListByPurchaseId(purchase.getId());
        // D-061：批次维度扣减，后录入批次先扣（id 倒序），扣不足钳零
        List<PurchaseItem> ordered = new ArrayList<>(items);
        ordered.sort(Comparator.comparing(PurchaseItem::getId).reversed());
        List<PurchaseItem> keptItems = new ArrayList<>();
        for (PurchaseItem item : ordered) {
            String key = purchaseSummaryKey(item);
            BigDecimal remaining = deductMap.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal qty = Optional.ofNullable(item.getQuantity()).orElse(BigDecimal.ZERO);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                keptItems.add(item);
                continue;
            }
            BigDecimal take = remaining.min(qty);
            BigDecimal newQty = qty.subtract(take);
            deductMap.put(key, remaining.subtract(take));
            if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                // 扣至 0/负（含单据被手工编辑后数量小于应扣量）→ 删除该批次行
                purchaseItemMapper.deletePurchaseItemByIds(new Long[]{item.getId()});
                continue;
            }
            item.setQuantity(newQty);
            item.setSubtotal(newQty.multiply(Optional.ofNullable(item.getUnitPrice()).orElse(BigDecimal.ZERO)));
            purchaseItemMapper.updatePurchaseItem(item);
            keptItems.add(item);
        }

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
     * 查询该订单配送日期对应的当日采购单（D-061：一天一单，按 order_date 反查，取非作废最新一张；
     * 无配送日期或未建单返回 null）
     */
    private PurchaseOrder findDayPurchase(SaleOrder saleOrder) {
        if (saleOrder == null || saleOrder.getDeliveryDate() == null) {
            return null;
        }
        return purchaseOrderMapper.selectActiveByOrderDate(saleOrder.getDeliveryDate());
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

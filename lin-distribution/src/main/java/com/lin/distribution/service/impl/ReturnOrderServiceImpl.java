package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.AcceptanceStatus;
import com.lin.distribution.constant.ReturnOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.AcceptanceItem;
import com.lin.distribution.domain.DeliverySourceItem;
import com.lin.distribution.domain.ReturnItem;
import com.lin.distribution.domain.ReturnOrder;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.dto.ReturnInspectDTO;
import com.lin.distribution.dto.ReturnOrderSaveDTO;
import com.lin.distribution.mapper.AcceptanceItemMapper;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.DeliverySourceItemMapper;
import com.lin.distribution.mapper.ReturnItemMapper;
import com.lin.distribution.mapper.ReturnOrderMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.ReturnOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 退货单Service业务层处理（S14/T6，D-032/D-034/Q31：独立退货单，不撤回历史验收）
 * 数量上限=实收-累计已退（草稿占用计入防并发超退）；单价后端锁原验收价；金额后端重算；
 * settle_scope 提交时快照（任一来源订单已结算→1下期冲销，否则0当期冲销）。
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnOrderServiceImpl implements ReturnOrderService {

    /** 质检结论：1可再售(入库) */
    private static final int QUALITY_REUSABLE = 1;
    /** 质检结论：2不可再售(报损) */
    private static final int QUALITY_DAMAGED = 2;

    private final ReturnOrderMapper returnOrderMapper;
    private final ReturnItemMapper returnItemMapper;
    private final AcceptanceMapper acceptanceMapper;
    private final AcceptanceItemMapper acceptanceItemMapper;
    private final DeliverySourceItemMapper deliverySourceItemMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final BizCodeService bizCodeService;

    @Override
    public List<ReturnOrder> selectReturnOrderList(ReturnOrder returnOrder) {
        return returnOrderMapper.selectReturnOrderList(returnOrder);
    }

    @Override
    public ReturnOrder selectReturnOrderById(Long id) {
        return returnOrderMapper.selectReturnOrderById(id);
    }

    @Override
    public List<ReturnItem> selectItemsByReturnId(Long returnId) {
        return returnItemMapper.selectListByReturnId(returnId);
    }

    @Override
    @Transactional
    public ReturnOrder create(ReturnOrderSaveDTO dto) {
        if (dto == null || dto.getAcceptanceId() == null) {
            throw new ServiceException("原验收单不能为空");
        }
        Acceptance acceptance = getSubmittableAcceptance(dto.getAcceptanceId());

        List<ReturnItem> items = buildItems(dto.getItems(), acceptance, null);
        BigDecimal total = items.stream().map(ReturnItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ReturnOrder order = new ReturnOrder();
        order.setCode(bizCodeService.nextDailyCode("returnOrder", "TH", 3));
        order.setCustomerId(acceptance.getCustomerId());
        order.setCustomerDeptId(dto.getCustomerDeptId() != null
                ? dto.getCustomerDeptId() : acceptance.getDeliveryPointId());
        order.setDeliveryId(acceptance.getDeliveryOrderId());
        order.setAcceptanceId(acceptance.getId());
        order.setReturnDate(dto.getReturnDate() != null ? dto.getReturnDate() : LocalDate.now());
        order.setTotalAmount(total);
        order.setStatus(ReturnOrderStatus.DRAFT.getCode());
        order.setRemark(dto.getRemark());
        order.setCreateBy(resolveOperator());
        order.setCreateTime(DateUtils.getNowDate());
        returnOrderMapper.insertReturnOrder(order);

        items.forEach(item -> item.setReturnId(order.getId()));
        returnItemMapper.batchInsertReturnItem(items);
        log.info("[return create] 退货单 {} 创建，验收单 {}，共 {} 行，金额 {}",
                order.getCode(), acceptance.getCode(), items.size(), total);
        return order;
    }

    @Override
    @Transactional
    public ReturnOrder updateDraft(ReturnOrderSaveDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new ServiceException("退货单ID不能为空");
        }
        ReturnOrder exist = getExistReturnOrder(dto.getId());
        if (!Objects.equals(exist.getStatus(), ReturnOrderStatus.DRAFT.getCode())) {
            throw new ServiceException("仅草稿状态可修改");
        }
        Acceptance acceptance = getSubmittableAcceptance(exist.getAcceptanceId());

        List<ReturnItem> items = buildItems(dto.getItems(), acceptance, exist.getId());
        BigDecimal total = items.stream().map(ReturnItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 明细整体重建（快照/价格/金额以本次重算为准）
        returnItemMapper.deleteByReturnId(exist.getId());
        items.forEach(item -> item.setReturnId(exist.getId()));
        returnItemMapper.batchInsertReturnItem(items);

        ReturnOrder update = new ReturnOrder();
        update.setId(exist.getId());
        update.setCustomerDeptId(dto.getCustomerDeptId() != null
                ? dto.getCustomerDeptId() : exist.getCustomerDeptId());
        update.setReturnDate(dto.getReturnDate() != null ? dto.getReturnDate() : exist.getReturnDate());
        update.setTotalAmount(total);
        update.setRemark(dto.getRemark());
        update.setUpdateBy(resolveOperator());
        update.setUpdateTime(DateUtils.getNowDate());
        returnOrderMapper.updateReturnOrder(update);

        exist.setTotalAmount(total);
        exist.setReturnDate(update.getReturnDate());
        return exist;
    }

    @Override
    @Transactional
    public ReturnOrder submit(Long id) {
        ReturnOrder exist = getExistReturnOrder(id);
        if (!Objects.equals(exist.getStatus(), ReturnOrderStatus.DRAFT.getCode())) {
            throw new ServiceException("仅草稿状态可提交");
        }
        List<ReturnItem> items = returnItemMapper.selectListByReturnId(id);
        if (CollectionUtils.isEmpty(items)) {
            throw new ServiceException("退货明细不能为空");
        }

        // 复核数量上限（排除自身占用，防并发超退）
        Acceptance acceptance = getSubmittableAcceptance(exist.getAcceptanceId());
        checkQuantityBounds(items.stream()
                .map(item -> {
                    ReturnOrderSaveDTO.Item dtoItem = new ReturnOrderSaveDTO.Item();
                    dtoItem.setAcceptanceItemId(item.getAcceptanceItemId());
                    dtoItem.setReturnQuantity(item.getReturnQuantity());
                    return dtoItem;
                })
                .collect(Collectors.toList()), acceptance, exist.getId());

        // settle_scope 结算口径快照：任一来源订单已结算 → 1 下期冲销；否则 0 当期冲销
        ReturnOrder update = new ReturnOrder();
        update.setId(id);
        update.setStatus(ReturnOrderStatus.SUBMITTED.getCode());
        update.setSettleScope(resolveSettleScope(acceptance.getDeliveryOrderId()));
        update.setUpdateBy(resolveOperator());
        update.setUpdateTime(DateUtils.getNowDate());
        returnOrderMapper.updateReturnOrder(update);

        exist.setStatus(update.getStatus());
        exist.setSettleScope(update.getSettleScope());
        log.info("[return submit] 退货单 {} 已提交，settle_scope={}", exist.getCode(), update.getSettleScope());
        return exist;
    }

    @Override
    @Transactional
    public ReturnOrder inspect(Long id, ReturnInspectDTO dto) {
        if (dto == null || CollectionUtils.isEmpty(dto.getItems())) {
            throw new ServiceException("质检结论不能为空");
        }
        ReturnOrder exist = getExistReturnOrder(id);
        if (!Objects.equals(exist.getStatus(), ReturnOrderStatus.SUBMITTED.getCode())) {
            throw new ServiceException("仅已提交(质检中)的退货单可质检");
        }

        Map<Long, ReturnItem> itemMap = returnItemMapper.selectListByReturnId(id).stream()
                .collect(Collectors.toMap(ReturnItem::getId, item -> item));
        for (ReturnInspectDTO.Item dtoItem : dto.getItems()) {
            if (dtoItem.getItemId() == null || !itemMap.containsKey(dtoItem.getItemId())) {
                throw new ServiceException("退货明细不存在：" + dtoItem.getItemId());
            }
            if (!Objects.equals(dtoItem.getQualityResult(), QUALITY_REUSABLE)
                    && !Objects.equals(dtoItem.getQualityResult(), QUALITY_DAMAGED)) {
                throw new ServiceException("质检结论仅支持：1可再售入库 / 2不可再售报损");
            }
            ReturnItem update = new ReturnItem();
            update.setId(dtoItem.getItemId());
            update.setQualityResult(dtoItem.getQualityResult());
            update.setQualityNote(StringUtils.trimToNull(dtoItem.getQualityNote()));
            returnItemMapper.updateReturnItem(update);
        }

        ReturnOrder update = new ReturnOrder();
        update.setId(id);
        update.setStatus(ReturnOrderStatus.INSPECTED.getCode());
        update.setInspectedBy(resolveOperator());
        update.setInspectedTime(DateUtils.getNowDate());
        update.setUpdateTime(DateUtils.getNowDate());
        returnOrderMapper.updateReturnOrder(update);

        exist.setStatus(update.getStatus());
        exist.setInspectedBy(update.getInspectedBy());
        exist.setInspectedTime(update.getInspectedTime());
        log.info("[return inspect] 退货单 {} 质检完成，共 {} 行", exist.getCode(), dto.getItems().size());
        return exist;
    }

    @Override
    @Transactional
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        int count = 0;
        for (Long id : ids) {
            ReturnOrder exist = returnOrderMapper.selectReturnOrderById(id);
            if (exist == null) {
                continue;
            }
            if (!Objects.equals(exist.getStatus(), ReturnOrderStatus.DRAFT.getCode())) {
                throw new ServiceException("仅草稿状态可删除：" + exist.getCode());
            }
            returnItemMapper.deleteByReturnId(id);
            count += returnOrderMapper.deleteReturnOrderById(id);
        }
        return count;
    }

    /**
     * 已提交验收单才能退货（实收已生效，历史验收不撤回 D-032）
     */
    private Acceptance getSubmittableAcceptance(Long acceptanceId) {
        Acceptance acceptance = acceptanceMapper.selectAcceptanceById(acceptanceId);
        if (acceptance == null) {
            throw new ServiceException("原验收单不存在");
        }
        if (!Objects.equals(acceptance.getStatus(), AcceptanceStatus.SUBMITTED.getCode())) {
            throw new ServiceException("仅已提交的验收单可发起退货");
        }
        return acceptance;
    }

    private ReturnOrder getExistReturnOrder(Long id) {
        ReturnOrder exist = returnOrderMapper.selectReturnOrderById(id);
        if (exist == null) {
            throw new ServiceException("退货单不存在");
        }
        return exist;
    }

    /**
     * 构建退货明细：快照取自验收明细行，单价锁原验收价（忽略前端价），
     * 数量上限=实收-累计已退（excludeReturnId 非空时排除该退货单自身占用）。
     */
    private List<ReturnItem> buildItems(List<ReturnOrderSaveDTO.Item> dtoItems,
                                        Acceptance acceptance, Long excludeReturnId) {
        checkQuantityBounds(dtoItems, acceptance, excludeReturnId);
        List<ReturnItem> items = new ArrayList<>();
        for (ReturnOrderSaveDTO.Item dtoItem : dtoItems) {
            AcceptanceItem accItem = acceptanceItemMapper.selectAcceptanceItemById(dtoItem.getAcceptanceItemId());
            ReturnItem item = new ReturnItem();
            item.setAcceptanceItemId(accItem.getId());
            item.setSkuId(accItem.getSkuId());
            item.setProductName(accItem.getProductName());
            item.setProductSpec(accItem.getProductSpec());
            item.setProductUnit(accItem.getProductUnit());
            item.setReturnQuantity(dtoItem.getReturnQuantity());
            item.setUnitPrice(nvl(accItem.getUnitPrice())); // 单价锁原验收价
            item.setAmount(scale(item.getUnitPrice().multiply(item.getReturnQuantity())));
            items.add(item);
        }
        return items;
    }

    /**
     * 数量上限校验（§七：退货数量上限 = 实收 - Σ该验收项已退量）。
     * 已完成(status=3)的退货单不再占用；同一验收明细行可拆多张退货单。
     */
    private void checkQuantityBounds(List<ReturnOrderSaveDTO.Item> dtoItems,
                                     Acceptance acceptance, Long excludeReturnId) {
        if (CollectionUtils.isEmpty(dtoItems)) {
            throw new ServiceException("退货明细不能为空");
        }
        List<Long> accItemIds = dtoItems.stream()
                .map(ReturnOrderSaveDTO.Item::getAcceptanceItemId)
                .collect(Collectors.toList());
        Map<Long, BigDecimal> returnedMap = returnItemMapper
                .sumReturnedByAcceptanceItemIds(accItemIds, excludeReturnId).stream()
                .collect(Collectors.toMap(ReturnItem::getAcceptanceItemId,
                        item -> nvl(item.getReturnQuantity()), (a, b) -> a, HashMap::new));
        for (ReturnOrderSaveDTO.Item dtoItem : dtoItems) {
            if (dtoItem.getReturnQuantity() == null
                    || dtoItem.getReturnQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ServiceException("退货数量必须大于0");
            }
            AcceptanceItem accItem = acceptanceItemMapper.selectAcceptanceItemById(dtoItem.getAcceptanceItemId());
            if (accItem == null || !Objects.equals(accItem.getAcceptanceId(), acceptance.getId())) {
                throw new ServiceException("退货明细不属于该验收单");
            }
            BigDecimal available = nvl(accItem.getActualQuantity())
                    .subtract(returnedMap.getOrDefault(accItem.getId(), BigDecimal.ZERO));
            if (dtoItem.getReturnQuantity().compareTo(available) > 0) {
                throw new ServiceException(String.format(
                        "退货数量超上限：%s 可退 %s，本次申请 %s",
                        accItem.getProductName(), available.stripTrailingZeros().toPlainString(),
                        dtoItem.getReturnQuantity().stripTrailingZeros().toPlainString()));
            }
        }
    }

    /**
     * settle_scope 快照（Q32/D-033）：来源订单（送货单 source_item 去重）任一已结算 → 1 下期冲销；
     * 历史单无来源台账按 0 当期冲销处理
     */
    private Integer resolveSettleScope(Long deliveryOrderId) {
        List<Long> orderIds = deliverySourceItemMapper.selectListByDeliveryId(deliveryOrderId).stream()
                .map(DeliverySourceItem::getSaleOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(orderIds)) {
            return 0;
        }
        boolean anySettled = saleOrderMapper.selectSaleOrderByIdIn(orderIds).stream()
                .anyMatch(order -> SaleOrderStatus.SETTLED.getCode().equals(order.getStatus()));
        return anySettled ? 1 : 0;
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

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return NumberUtils.toScaledBigDecimal(value, 2, RoundingMode.HALF_UP);
    }
}

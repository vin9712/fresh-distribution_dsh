package com.lin.distribution.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.constant.PurchaseOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.PurchaseItem;
import com.lin.distribution.domain.PurchaseOrder;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.dto.PurchaseByOrdersDTO;
import com.lin.distribution.dto.PurchaseGenerateDTO;
import com.lin.distribution.mapper.PurchaseItemMapper;
import com.lin.distribution.mapper.PurchaseOrderMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.PurchaseOrderService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购单Service业务层处理
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {
    /** 来源类型：自动生成 */
    private static final int SOURCE_TYPE_AUTO = 1;
    /** 来源类型：手工创建 */
    private static final int SOURCE_TYPE_MANUAL = 2;

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseItemMapper purchaseItemMapper;
    private final SaleOrderMapper saleOrderMapper;
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

    @Override
    @Transactional
    public PurchaseOrder generateByOrderIds(PurchaseByOrdersDTO dto) {
        if (dto == null || CollectionUtils.isEmpty(dto.getOrderIds())) {
            throw new ServiceException("请选择要生成采购单的订单");
        }
        List<Long> orderIds = dto.getOrderIds().stream().distinct().collect(Collectors.toList());

        // 校验：订单存在且均已确认，配送日期一致（采购单归属日期 = 配送日期）
        List<SaleOrder> orders = saleOrderMapper.selectSaleOrderByIdIn(orderIds);
        if (orders.size() != orderIds.size()) {
            throw new ServiceException("部分订单不存在或已删除，请刷新列表后重试");
        }
        String invalidCodes = orders.stream()
                .filter(o -> !SaleOrderStatus.CONFIRMED.getCode().equals(o.getStatus()))
                .map(SaleOrder::getCode)
                .collect(Collectors.joining(","));
        if (StringUtils.isNotEmpty(invalidCodes)) {
            throw new ServiceException("以下订单不是审核状态，无法生成采购单：" + invalidCodes);
        }
        Set<LocalDate> dates = orders.stream().map(SaleOrder::getDeliveryDate).collect(Collectors.toSet());
        if (dates.size() > 1) {
            throw new ServiceException("所选订单配送日期不一致，请按配送日期分批生成采购单");
        }
        LocalDate orderDate = orders.get(0).getDeliveryDate();

        // 幂等：任一选中订单已存在于自动采购单 source_order_ids 则拒绝
        checkOrdersNotInAutoPurchase(orderIds);

        List<PurchaseItem> summaryItems = purchaseItemMapper.selectSummaryByOrderIds(orderIds);
        if (CollectionUtils.isEmpty(summaryItems)) {
            throw new ServiceException("所选订单没有可汇总的商品明细，无法生成采购单");
        }

        String code = bizCodeService.nextDailyCode("purchase", "PC", 3);
        BigDecimal totalAmount = calcTotalAmount(summaryItems);

        PurchaseOrder order = new PurchaseOrder();
        order.setCode(code);
        order.setOrderDate(orderDate);
        order.setSourceType(SOURCE_TYPE_AUTO);
        order.setSourceOrderIds(JSON.toJSONString(orderIds));
        order.setSupplierName(dto.getSupplierName());
        order.setPurchaser(dto.getPurchaser());
        order.setTotalAmount(totalAmount);
        order.setStatus(PurchaseOrderStatus.DRAFT.getCode());
        order.setCreateTime(DateUtils.getNowDate());
        purchaseOrderMapper.insertPurchaseOrder(order);

        fillItemsAndSave(order.getId(), summaryItems);

        return order;
    }

    /**
     * 幂等校验：已有自动采购单引用任一选中订单则抛异常（含按日期生成的旧单）
     */
    private void checkOrdersNotInAutoPurchase(List<Long> orderIds) {
        PurchaseOrder query = new PurchaseOrder();
        query.setSourceType(SOURCE_TYPE_AUTO);
        List<PurchaseOrder> existList = purchaseOrderMapper.selectPurchaseOrderList(query);
        if (CollectionUtils.isEmpty(existList)) {
            return;
        }
        Set<Long> target = new HashSet<>(orderIds);
        for (PurchaseOrder po : existList) {
            if (StringUtils.isEmpty(po.getSourceOrderIds())) {
                continue;
            }
            List<Long> ids;
            try {
                ids = JSON.parseArray(po.getSourceOrderIds(), Long.class);
            } catch (Exception ignored) {
                // source_order_ids 非 JSON 的历史数据跳过
                continue;
            }
            for (Long id : ids) {
                if (target.contains(id)) {
                    throw new ServiceException("订单已包含在采购单【" + po.getCode() + "】中，请勿重复生成");
                }
            }
        }
    }

    @Override
    @Transactional
    public PurchaseOrder generateByOrderDate(PurchaseGenerateDTO dto) {
        LocalDate orderDate = dto.getOrderDate();
        if (orderDate == null) {
            throw new ServiceException("采购日期不能为空");
        }
        checkAutoPurchaseNotExist(orderDate);

        List<PurchaseItem> summaryItems = purchaseItemMapper.selectSummaryByDeliveryDate(orderDate);
        if (CollectionUtils.isEmpty(summaryItems)) {
            throw new ServiceException("该配送日期没有已确认订单明细，无法生成采购单");
        }

        List<Long> sourceOrderIds = purchaseOrderMapper.selectSourceOrderIdsByDeliveryDate(orderDate);

        String code = bizCodeService.nextDailyCode("purchase", "PC", 3);

        BigDecimal totalAmount = calcTotalAmount(summaryItems);

        PurchaseOrder order = new PurchaseOrder();
        order.setCode(code);
        order.setOrderDate(orderDate);
        order.setSourceType(SOURCE_TYPE_AUTO);
        order.setSourceOrderIds(JSON.toJSONString(sourceOrderIds));
        order.setTotalAmount(totalAmount);
        order.setStatus(PurchaseOrderStatus.DRAFT.getCode());
        order.setCreateTime(DateUtils.getNowDate());
        purchaseOrderMapper.insertPurchaseOrder(order);

        fillItemsAndSave(order.getId(), summaryItems);

        return order;
    }

    @Override
    @Transactional
    public int createPurchase(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            throw new ServiceException("采购单不能为空");
        }
        List<PurchaseItem> items = purchaseOrder.getItems();
        if (CollectionUtils.isEmpty(items)) {
            throw new ServiceException("采购明细不能为空");
        }
        if (purchaseOrder.getOrderDate() == null) {
            throw new ServiceException("采购日期不能为空");
        }

        purchaseOrder.setCode(bizCodeService.nextDailyCode("purchase", "PC", 3));
        purchaseOrder.setSourceType(SOURCE_TYPE_MANUAL);
        purchaseOrder.setSourceOrderIds(null);
        purchaseOrder.setStatus(PurchaseOrderStatus.DRAFT.getCode());
        purchaseOrder.setTotalAmount(calcTotalAmount(items));
        purchaseOrder.setCreateTime(DateUtils.getNowDate());
        int rows = purchaseOrderMapper.insertPurchaseOrder(purchaseOrder);

        fillItemsAndSave(purchaseOrder.getId(), items);

        return rows;
    }

    @Override
    @Transactional
    public int updatePurchase(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null || purchaseOrder.getId() == null) {
            throw new ServiceException("采购单ID不能为空");
        }
        PurchaseOrder exist = getExistPurchaseOrder(purchaseOrder.getId());
        if (!PurchaseOrderStatus.DRAFT.getCode().equals(exist.getStatus())) {
            throw new ServiceException("仅草稿状态可修改");
        }
        List<PurchaseItem> items = purchaseOrder.getItems();
        if (CollectionUtils.isEmpty(items)) {
            throw new ServiceException("采购明细不能为空");
        }

        // 锁定不可变字段（单号、日期、来源、来源订单、状态）
        purchaseOrder.setCode(exist.getCode());
        purchaseOrder.setOrderDate(exist.getOrderDate());
        purchaseOrder.setSourceType(exist.getSourceType());
        purchaseOrder.setSourceOrderIds(exist.getSourceOrderIds());
        purchaseOrder.setStatus(exist.getStatus());
        purchaseOrder.setTotalAmount(calcTotalAmount(items));
        purchaseOrder.setUpdateTime(DateUtils.getNowDate());
        int rows = purchaseOrderMapper.updatePurchaseOrder(purchaseOrder);

        purchaseItemMapper.deletePurchaseItemByPurchaseId(purchaseOrder.getId());
        fillItemsAndSave(purchaseOrder.getId(), items);

        return rows;
    }

    @Override
    @Transactional
    public int confirm(Long id) {
        PurchaseOrder exist = getExistPurchaseOrder(id);
        if (!PurchaseOrderStatus.DRAFT.getCode().equals(exist.getStatus())) {
            throw new ServiceException("仅草稿状态可确认");
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

    /**
     * 幂等检查：当日已存在自动生成的草稿采购单则抛异常
     */
    private void checkAutoPurchaseNotExist(LocalDate orderDate) {
        PurchaseOrder query = new PurchaseOrder();
        query.setOrderDate(orderDate);
        query.setSourceType(SOURCE_TYPE_AUTO);
        query.setStatus(PurchaseOrderStatus.DRAFT.getCode());
        List<PurchaseOrder> existList = purchaseOrderMapper.selectPurchaseOrderList(query);
        if (CollectionUtils.isNotEmpty(existList)) {
            throw new ServiceException("该日期采购单已生成");
        }
    }

    /**
     * 计算采购总额，并回填每条明细的小计与排序
     */
    private BigDecimal calcTotalAmount(List<PurchaseItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        int sort = 0;
        for (PurchaseItem item : items) {
            BigDecimal quantity = Optional.ofNullable(item.getQuantity()).orElse(BigDecimal.ZERO);
            BigDecimal unitPrice = Optional.ofNullable(item.getUnitPrice()).orElse(BigDecimal.ZERO);
            BigDecimal subtotal = NumberUtils.toScaledBigDecimal(quantity.multiply(unitPrice), 2, RoundingMode.HALF_UP);
            item.setSubtotal(subtotal);
            item.setSort(sort++);
            total = total.add(subtotal);
        }
        return total;
    }

    /**
     * 回填采购单ID并批量保存明细
     */
    private void fillItemsAndSave(Long purchaseId, List<PurchaseItem> items) {
        for (PurchaseItem item : items) {
            item.setId(null);
            item.setPurchaseId(purchaseId);
        }
        purchaseItemMapper.insertPurchaseItemBatch(items);
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
}

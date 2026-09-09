package com.lin.distribution.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.AcceptanceStatus;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.DeliveryScopeType;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.AcceptanceItem;
import com.lin.distribution.domain.AcceptanceRevokeLog;
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.DeliverySourceItem;
import com.lin.distribution.domain.ReturnItem;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.AcceptanceUpdateDTO;
import com.lin.distribution.mapper.AcceptanceItemMapper;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.AcceptanceRevokeLogMapper;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliverySourceItemMapper;
import com.lin.distribution.mapper.MonthSettlementMapper;
import com.lin.distribution.mapper.ReturnItemMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.domain.MonthSettlement;
import com.lin.distribution.dto.AcceptanceQuickAcceptDTO;
import com.lin.distribution.service.AcceptanceService;
import com.lin.distribution.vo.AcceptanceByOrderVO;
import com.lin.distribution.vo.DeliverySourceVO;
import com.lin.distribution.service.BizCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 验收单Service业务层处理（DESIGN.md §9 / S14 T5 验收口径）
 * 实收金额=actual_quantity×unit_price（后端重算）；
 * 差异=实收−送货（双向差异均必填原因：负=短收 reason_type=1，正=超收 reason_type=2，D-013/G8）；
 * A类总单明细按「配送点×商品」展开（§3.2③ 录入即归属，数据源=source_item 点级聚合）。
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcceptanceServiceImpl implements AcceptanceService {

    /** 差异原因类型：1短收（acceptance_shortfall_reason） */
    private static final int REASON_TYPE_SHORTFALL = 1;
    /** 差异原因类型：2超收（acceptance_overage_reason） */
    private static final int REASON_TYPE_OVERAGE = 2;

    private final AcceptanceMapper acceptanceMapper;
    private final AcceptanceItemMapper acceptanceItemMapper;
    private final AcceptanceRevokeLogMapper acceptanceRevokeLogMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    private final DeliverySourceItemMapper deliverySourceItemMapper;
    private final CustomerDeptMapper customerDeptMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderDetailMapper saleOrderDetailMapper;
    private final ReturnItemMapper returnItemMapper;
    private final MonthSettlementMapper monthSettlementMapper;
    private final BizCodeService bizCodeService;

    @Override
    public Acceptance selectAcceptanceById(Long id) {
        return acceptanceMapper.selectAcceptanceById(id);
    }

    @Override
    public List<Acceptance> selectAcceptanceList(Acceptance acceptance) {
        return acceptanceMapper.selectAcceptanceList(acceptance);
    }

    @Override
    public List<AcceptanceItem> selectItemListByAcceptanceId(Long acceptanceId) {
        List<AcceptanceItem> items = acceptanceItemMapper.selectListByAcceptanceId(acceptanceId);
        fillSources(items);
        fillReturnedQuantity(items);
        fillOrderInfo(items);
        return items;
    }

    /**
     * 订单信息回填（AC-6，新口径验收行=订单明细行）：按 sale_order_detail_id 反查来源订单号、
     * 按 customer_dept_id 回填配送点名（防 N+1，批量查询）。历史单行（两字段均空）不受影响。
     */
    private void fillOrderInfo(List<AcceptanceItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> detailIds = items.stream()
                .map(AcceptanceItem::getSaleOrderDetailId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        if (!detailIds.isEmpty()) {
            Map<Long, SaleOrderDetail> detailById = saleOrderDetailMapper.selectByIdIn(detailIds).stream()
                    .filter(d -> d.getId() != null)
                    .collect(Collectors.toMap(SaleOrderDetail::getId, d -> d, (a, b) -> a));
            for (AcceptanceItem item : items) {
                if (item.getSaleOrderDetailId() != null) {
                    SaleOrderDetail d = detailById.get(item.getSaleOrderDetailId());
                    if (d != null) {
                        item.setOrderCode(StringUtils.defaultString(d.getOrderCode()));
                        item.setChangeType(d.getChangeType());
                        item.setChangeRemark(d.getChangeRemark());
                    }
                }
            }
        }
        List<Long> deptIds = items.stream()
                .map(AcceptanceItem::getCustomerDeptId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        if (!deptIds.isEmpty()) {
            Map<Long, String> deptNameById = customerDeptMapper.selectCustomerDeptByIds(deptIds).stream()
                    .collect(Collectors.toMap(CustomerDept::getId, CustomerDept::getName, (a, b) -> a));
            for (AcceptanceItem item : items) {
                if (item.getCustomerDeptId() != null) {
                    item.setCustomerDeptName(deptNameById.get(item.getCustomerDeptId()));
                }
            }
        }
    }

    /**
     * 来源对照列（S14 §八）：按验收行的 (delivery_detail_id, customer_dept_id) 匹配 source_item 分配行，
     * 附来源订单号/下单数量（=分配量，一订单行只送一次全量分配）/下单单价快照；
     * 历史单无台账 → sources 为空列表，前端展示"—历史数据—"。
     */
    private void fillSources(List<AcceptanceItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Long deliveryOrderId = null;
        for (AcceptanceItem item : items) {
            if (item.getDeliveryItemId() != null && item.getAcceptanceId() != null) {
                deliveryOrderId = findDeliveryOrderId(item.getAcceptanceId());
                break;
            }
        }
        if (deliveryOrderId == null) {
            return;
        }
        List<DeliverySourceItem> sources = deliverySourceItemMapper.selectListByDeliveryId(deliveryOrderId);
        if (sources.isEmpty()) {
            return;
        }
        // 批量回填来源订单号（防 N+1，同 DeliveryOrderServiceImpl.selectDeliverySources 惯例）
        List<Long> orderIds = sources.stream()
                .map(DeliverySourceItem::getSaleOrderId).distinct().collect(Collectors.toList());
        Map<Long, String> orderCodeMap = saleOrderMapper.selectSaleOrderByIdIn(orderIds).stream()
                .collect(Collectors.toMap(SaleOrder::getId, SaleOrder::getCode, (a, b) -> a));

        Map<String, List<DeliverySourceVO.SourceRow>> byItemKey = sources.stream()
                .collect(Collectors.groupingBy(
                        si -> si.getDeliveryDetailId() + "|" + si.getCustomerDeptId(),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            List<DeliverySourceVO.SourceRow> rows = new ArrayList<>();
                            for (DeliverySourceItem si : list) {
                                DeliverySourceVO.SourceRow row = new DeliverySourceVO.SourceRow();
                                row.setSaleOrderId(si.getSaleOrderId());
                                row.setOrderCode(orderCodeMap.get(si.getSaleOrderId()));
                                row.setCustomerDeptId(si.getCustomerDeptId());
                                row.setAllocatedQuantity(si.getAllocatedQuantity());
                                row.setUnitPrice(si.getUnitPrice());
                                rows.add(row);
                            }
                            return rows;
                        })));
        for (AcceptanceItem item : items) {
            List<DeliverySourceVO.SourceRow> rows =
                    byItemKey.get(item.getDeliveryItemId() + "|" + item.getCustomerDeptId());
            if (rows != null) {
                item.setSources(rows);
            }
        }
    }

    /** 查验收单所属送货单ID（fillSources 用） */
    private Long findDeliveryOrderId(Long acceptanceId) {
        Acceptance acceptance = acceptanceMapper.selectAcceptanceById(acceptanceId);
        return acceptance == null ? null : acceptance.getDeliveryOrderId();
    }

    /**
     * 累计已退回填（退货单页面可退量=实收−累计已退）：
     * 含草稿/已提交退货单占用，仅 status=3 已完成释放（同 ReturnOrderServiceImpl 口径）。
     */
    private void fillReturnedQuantity(List<AcceptanceItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> itemIds = items.stream()
                .map(AcceptanceItem::getId).filter(Objects::nonNull).collect(Collectors.toList());
        if (itemIds.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> returnedMap = returnItemMapper.sumReturnedByAcceptanceItemIds(itemIds, null).stream()
                .filter(ri -> ri.getAcceptanceItemId() != null)
                .collect(Collectors.toMap(ReturnItem::getAcceptanceItemId, ReturnItem::getReturnQuantity, (a, b) -> a));
        for (AcceptanceItem item : items) {
            item.setReturnedQuantity(returnedMap.getOrDefault(item.getId(), BigDecimal.ZERO));
        }
    }

    /**
     * 「去验收」定位（OA 订单维度优先 → 客户日过渡兼容 → 历史送货单回退，详见接口注释）。
     */
    @Override
    public AcceptanceByOrderVO locateBySaleOrder(Long orderId) {
        AcceptanceByOrderVO vo = new AcceptanceByOrderVO();
        vo.setOrderId(orderId);
        vo.setHasAcceptance(false);
        if (orderId == null) {
            return vo;
        }

        SaleOrder order = saleOrderMapper.selectSaleOrderById(orderId);
        if (order != null) {
            // ① OA 订单维度优先：sale_order_id 反查一订单一验的验收单
            Acceptance orderAcc = acceptanceMapper.selectBySaleOrder(orderId);
            if (orderAcc != null) {
                fillOrderView(vo, order.getCustomerId(), order.getDeliveryDate());
                vo.setHasAcceptance(true);
                vo.setAcceptanceId(orderAcc.getId());
                vo.setAcceptanceCode(orderAcc.getCode());
                vo.setAcceptanceStatus(orderAcc.getStatus());
                return vo;
            }

            // ② 客户日维度过渡兼容（AC-5 既有）：该客户日已有客户日验收单 → 定位过去（只读打开，不再新建该维度）
            if (order.getDeliveryDate() != null) {
                fillOrderView(vo, order.getCustomerId(), order.getDeliveryDate());
                Acceptance hit = acceptanceMapper.selectByCustomerDate(order.getCustomerId(), order.getDeliveryDate());
                if (hit != null) {
                    vo.setHasAcceptance(true);
                    vo.setAcceptanceId(hit.getId());
                    vo.setAcceptanceCode(hit.getCode());
                    vo.setAcceptanceStatus(hit.getStatus());
                    return vo;
                }
                // 新流程订单（未进历史送货单体系）：带订单维度参数返回，前端引导一键建草稿
                if (!hasLegacyDeliveryTrail(orderId)) {
                    return vo;
                }
            } else {
                fillOrderView(vo, order.getCustomerId(), null);
            }
        }

        // ③ 历史回退（status=2 已配送的历史单 / 无订单行）：原送货单反查链路，只读维护
        List<Long> deliveryIds = legacyDeliveryIdsByOrder(orderId);
        if (deliveryIds.isEmpty()) {
            return vo;
        }
        List<DeliveryOrder> validDeliveries = deliveryOrderMapper.selectListByIds(deliveryIds)
                .stream()
                .filter(d -> !Objects.equals(d.getStatus(), DeliveryOrderStatus.VOIDED.getCode()))
                .sorted(Comparator.comparing(DeliveryOrder::getId))
                .collect(Collectors.toList());
        if (validDeliveries.isEmpty()) {
            return vo;
        }
        vo.setDeliveryIds(validDeliveries.stream().map(DeliveryOrder::getId).collect(Collectors.toList()));

        // 从最新单向前找已有验收单的单；都没有则定位最新单引导创建草稿
        DeliveryOrder hitDelivery = validDeliveries.get(validDeliveries.size() - 1);
        Acceptance hit = null;
        for (int i = validDeliveries.size() - 1; i >= 0; i--) {
            DeliveryOrder candidate = validDeliveries.get(i);
            Acceptance query = new Acceptance();
            query.setDeliveryOrderId(candidate.getId());
            List<Acceptance> accs = acceptanceMapper.selectAcceptanceList(query);
            if (CollectionUtils.isNotEmpty(accs)) {
                hit = accs.get(0);
                hitDelivery = candidate;
                break;
            }
        }

        vo.setDeliveryId(hitDelivery.getId());
        vo.setDeliveryCode(hitDelivery.getCode());
        vo.setDeliveryStatus(hitDelivery.getStatus());
        if (hit != null) {
            vo.setHasAcceptance(true);
            vo.setAcceptanceId(hit.getId());
            vo.setAcceptanceCode(hit.getCode());
            vo.setAcceptanceStatus(hit.getStatus());
        }
        return vo;
    }

    /** 该订单是否在历史送货单体系留有痕迹（source_item 台账 / 历史送货明细行 order_id） */
    private boolean hasLegacyDeliveryTrail(Long orderId) {
        return !legacyDeliveryIdsByOrder(orderId).isEmpty();
    }

    /** 订单视角定位公共回填（orderView 标记 + customerId + deliveryDate） */
    private void fillOrderView(AcceptanceByOrderVO vo, Long customerId, LocalDate deliveryDate) {
        vo.setOrderView(true);
        vo.setCustomerId(customerId);
        vo.setDeliveryDate(deliveryDate == null ? null : deliveryDate.toString());
    }
    /** 历史送货单反查：source_item 有效分配 → 历史送货明细行 order_id 回退（AC-5 只读维护用） */
    private List<Long> legacyDeliveryIdsByOrder(Long orderId) {
        DeliverySourceItem sourceQuery = new DeliverySourceItem();
        sourceQuery.setSaleOrderId(orderId);
        List<Long> deliveryIds = deliverySourceItemMapper.selectDeliverySourceItemList(sourceQuery)
                .stream()
                .map(DeliverySourceItem::getDeliveryId)
                .distinct()
                .collect(Collectors.toList());
        if (deliveryIds.isEmpty()) {
            deliveryIds = deliveryOrderDetailMapper.selectListByOrderIdIn(List.of(orderId))
                    .stream()
                    .map(DeliveryOrderDetail::getDeliveryId)
                    .distinct()
                    .collect(Collectors.toList());
        }
        return deliveryIds;
    }

    /**
     * 按送货单生成验收单草稿（一单一验，明细复制自送货单）：
     * A类总单按「配送点×商品」展开（source_item 点级聚合，delivered=SUM(allocated)）；
     * B/C类=送货明细行（同现状），明细行补记配送点（录入即归属）。
     */
    @Override
    @Transactional
    public Acceptance createByDeliveryOrder(Long deliveryOrderId) {
        if (deliveryOrderId == null) {
            throw new ServiceException("送货单ID不能为空");
        }
        DeliveryOrder deliveryOrder = deliveryOrderMapper.selectDeliveryOrderById(deliveryOrderId);
        if (deliveryOrder == null) {
            throw new ServiceException("送货单不存在");
        }
        if (!Objects.equals(deliveryOrder.getStatus(), DeliveryOrderStatus.DELIVERED.getCode())) {
            throw new ServiceException("仅已送达的送货单可生成验收单");
        }
        // 一单一验守卫
        if (acceptanceMapper.countByDeliveryOrderId(deliveryOrderId) > 0) {
            throw new ServiceException("该送货单已生成验收单，一单一验");
        }

        List<DeliveryOrderDetail> details = deliveryOrderDetailMapper.selectListByDeliveryId(deliveryOrderId);
        if (CollectionUtils.isEmpty(details)) {
            throw new ServiceException("送货单明细为空，无法生成验收单");
        }

        // S14/T5：A类总单按「配送点×商品」展开；B/C类按送货明细行展开
        List<AcceptanceItem> items = DeliveryScopeType.isCustomerDate(deliveryOrder.getScopeType())
                ? expandByPoint(deliveryOrder, details)
                : expandByDetail(deliveryOrder, details);

        BigDecimal total = BigDecimal.ZERO;
        int sort = 0;
        for (AcceptanceItem item : items) {
            item.setSort(sort++);
            item.setDifferenceQuantity(BigDecimal.ZERO);
            item.setActualAmount(scale(item.getUnitPrice().multiply(item.getActualQuantity())));
            total = total.add(item.getActualAmount());
        }

        Acceptance acceptance = new Acceptance();
        acceptance.setCode(bizCodeService.nextDailyCode("acceptance", "YS", 3));
        acceptance.setDeliveryOrderId(deliveryOrderId);
        acceptance.setCustomerId(deliveryOrder.getCustomerId());
        acceptance.setDeliveryPointId(deliveryOrder.getDeliveryPointId());
        acceptance.setAcceptDate(deliveryOrder.getDeliveryDate());
        acceptance.setTotalAmount(total);
        acceptance.setStatus(AcceptanceStatus.DRAFT.getCode());
        acceptance.setCreateTime(DateUtils.getNowDate());
        acceptanceMapper.insertAcceptance(acceptance);

        for (AcceptanceItem item : items) {
            item.setAcceptanceId(acceptance.getId());
        }
        acceptanceItemMapper.insertAcceptanceItemBatch(items);
        return acceptance;
    }

    /**
     * A类总单明细展开：source_item 按「配送点×商品(五元组)」聚合，delivered=SUM(allocated_quantity)，
     * 默认实收=应送；同五元组行必属同一送货行（生成侧合并保证），规格/单位快照取自该送货行。
     * 历史单（S14 前无 source_item）回退按送货明细行展开（dept 置空，页面显示"历史无来源"口径）。
     */
    private List<AcceptanceItem> expandByPoint(DeliveryOrder deliveryOrder, List<DeliveryOrderDetail> details) {
        List<DeliverySourceItem> sources = deliverySourceItemMapper.selectListByDeliveryId(deliveryOrder.getId());
        if (CollectionUtils.isEmpty(sources)) {
            log.warn("[acceptance create] A类送货单 {} 无来源台账（历史单），回退按送货明细行展开", deliveryOrder.getId());
            return expandByDetail(deliveryOrder, details);
        }
        // 五元组规格/单位快照在送货行上：同五元组必属同一 detail，按 detail 回填展示维度
        Map<Long, DeliveryOrderDetail> detailMap = details.stream()
                .collect(Collectors.toMap(DeliveryOrderDetail::getId, d -> d, (a, b) -> a));
        Map<String, List<DeliverySourceItem>> grouped = sources.stream()
                .collect(Collectors.groupingBy(this::pointMergeKey, LinkedHashMap::new, Collectors.toList()));
        List<AcceptanceItem> items = new ArrayList<>();
        for (List<DeliverySourceItem> group : grouped.values()) {
            DeliverySourceItem first = group.get(0);
            DeliveryOrderDetail detail = detailMap.get(first.getDeliveryDetailId());
            BigDecimal delivered = group.stream()
                    .map(si -> nvl(si.getAllocatedQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            AcceptanceItem item = new AcceptanceItem();
            item.setDeliveryItemId(first.getDeliveryDetailId());
            item.setCustomerDeptId(first.getCustomerDeptId());
            item.setSkuId(first.getSkuId());
            item.setProductName(first.getProductName());
            item.setProductSpec(detail == null ? null : detail.getProductSpec());
            item.setProductUnit(detail == null ? null : detail.getProductUnit());
            item.setDeliveredQuantity(delivered);
            item.setActualQuantity(delivered); // 默认实收=应送，只改有差异的点级行
            item.setUnitPrice(detail == null ? nvl(first.getUnitPrice()) : nvl(detail.getPrice()));
            items.add(item);
        }
        return items;
    }

    /**
     * 点级聚合键 = 配送点 + 送货行：生成侧五元组合并保证同一 detail 即同一商品五元组，
     * A类跨点合并行内的各点来源按 dept 拆开即得「点×商品」行（价格同 detail，无跨规格误并风险）
     */
    private String pointMergeKey(DeliverySourceItem si) {
        return (si.getCustomerDeptId() == null ? "_" : si.getCustomerDeptId())
                + "|" + (si.getDeliveryDetailId() == null ? "_" : si.getDeliveryDetailId());
    }

    /**
     * B/C类明细展开：一送货行一验收行，配送点=明细行 dept（空回退单据 delivery_point_id，兼容历史单）
     */
    private List<AcceptanceItem> expandByDetail(DeliveryOrder deliveryOrder, List<DeliveryOrderDetail> details) {
        List<AcceptanceItem> items = new ArrayList<>();
        for (DeliveryOrderDetail detail : details) {
            AcceptanceItem item = new AcceptanceItem();
            item.setDeliveryItemId(detail.getId());
            item.setCustomerDeptId(detail.getCustomerDeptId() != null
                    ? detail.getCustomerDeptId() : deliveryOrder.getDeliveryPointId());
            item.setSkuId(detail.getSkuId());
            item.setProductName(detail.getProductName());
            item.setProductSpec(detail.getProductSpec());
            item.setProductUnit(detail.getProductUnit());
            item.setDeliveredQuantity(nvl(detail.getNum()));
            item.setActualQuantity(nvl(detail.getNum()));
            item.setUnitPrice(nvl(detail.getPrice()));
            items.add(item);
        }
        return items;
    }

    /**
     * 按 客户+日期+配送点 生成验收单（D-055 原始版）。
     *
     * @deprecated 已升级为客户日维度（AC-1，《验收模块订单明细视角重构设计》），
     *             仅保留兼容旧调用（委托 createByCustomerDate），新代码禁用
     */
    @Deprecated
    @Override
    @Transactional
    public Acceptance createByCustomerPoint(Long customerId, Long customerDeptId, LocalDate deliveryDate) {
        return createByCustomerDate(customerId, deliveryDate, customerDeptId);
    }

    /**
     * 按「客户+配送日期」生成验收单草稿（AC-1/AC-2，订单明细视角）：一客户日一张，
     * 应送行=该客户当日全部订单明细行（跨配送点平铺，selectValidByCustomerDateForView）。
     */
    @Override
    @Transactional
    public Acceptance createByCustomerDate(Long customerId, LocalDate deliveryDate) {
        return createByCustomerDate(customerId, deliveryDate, null);
    }

    /**
     * 建单实现（AC-1/AC-2/AC-3）：deptId=null 即客户日维度（新口径唯一入口）；
     * 守卫用 selectByCustomerDate（含按点历史遗留，防混维度重复建单）；
     * 表头 delivery_point_id=deptId（客户日单为 NULL，展示「全部配送点」）。
     */
    private Acceptance createByCustomerDate(Long customerId, LocalDate deliveryDate, Long deptId) {
        if (customerId == null || deliveryDate == null) {
            throw new ServiceException("客户/配送日期不能为空");
        }
        // 一客户日一验守卫（AC-3）
        Acceptance existing = acceptanceMapper.selectByCustomerDate(customerId, deliveryDate);
        if (existing != null) {
            throw new ServiceException("该客户+配送日期已存在验收单【" + existing.getCode() + "】，一客户日一验");
        }
        List<SaleOrderDetail> orderDetails = deptId == null
                ? saleOrderDetailMapper.selectValidByCustomerDateForView(customerId, deliveryDate)
                : saleOrderDetailMapper.selectValidByCustomerPointDateForView(customerId, deptId, deliveryDate);
        if (CollectionUtils.isEmpty(orderDetails)) {
            throw new ServiceException("该客户+配送日期没有有效订单明细，无法生成验收单");
        }
        // 应送行=订单明细行（含标记）；退货行（change_type=3）应送=实收=0；
        // 加单行默认实收取 actual_num 镜像（现场已录），其余按应送兑底。
        List<AcceptanceItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int sort = 0;
        for (SaleOrderDetail d : orderDetails) {
            boolean isReturned = d.getChangeType() != null && d.getChangeType() == 3;
            BigDecimal delivered = isReturned ? BigDecimal.ZERO : nvl(d.getNum());
            BigDecimal actual = isReturned ? BigDecimal.ZERO
                    : (d.getActualNum() != null && d.getActualNum().compareTo(BigDecimal.ZERO) > 0
                            ? d.getActualNum() : delivered);
            AcceptanceItem item = new AcceptanceItem();
            item.setSaleOrderDetailId(d.getId());
            item.setCustomerDeptId(d.getCustomerDeptId());
            item.setSkuId(d.getSkuId());
            item.setProductName(d.getProductName());
            item.setProductSpec(d.getProductSpec());
            item.setProductUnit(d.getProductUnit());
            item.setDeliveredQuantity(delivered);
            item.setActualQuantity(actual);
            item.setUnitPrice(nvl(d.getProductPrice()));
            item.setDifferenceQuantity(actual.subtract(delivered));
            item.setSort(sort++);
            item.setActualAmount(scale(item.getUnitPrice().multiply(actual)));
            total = total.add(item.getActualAmount());
            items.add(item);
        }

        Acceptance acceptance = new Acceptance();
        acceptance.setCode(bizCodeService.nextDailyCode("acceptance", "YS", 3));
        acceptance.setCustomerId(customerId);
        acceptance.setDeliveryPointId(deptId);
        acceptance.setDeliveryDate(deliveryDate);
        acceptance.setAcceptDate(deliveryDate);
        acceptance.setTotalAmount(total);
        acceptance.setStatus(AcceptanceStatus.DRAFT.getCode());
        acceptance.setCreateTime(DateUtils.getNowDate());
        acceptanceMapper.insertAcceptance(acceptance);

        for (AcceptanceItem item : items) {
            item.setAcceptanceId(acceptance.getId());
        }
        acceptanceItemMapper.insertAcceptanceItemBatch(items);
        return acceptance;
    }

    /**
     * OA：按订单生成（或同步）验收草稿（《订单页一键验收链路设计》§4.3）：
     * 一订单一验；无单则建，有草稿则同步缺失行（幂等），已提交则拒绝。
     */
    @Override
    @Transactional
    public Acceptance createByOrder(Long orderId) {
        if (orderId == null) {
            throw new ServiceException("订单ID不能为空");
        }
        SaleOrder order = saleOrderMapper.selectSaleOrderById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() < SaleOrderStatus.CONFIRMED.getCode()) {
            throw new ServiceException("仅已确认及以后的订单可验收");
        }
        Acceptance existing = acceptanceMapper.selectBySaleOrder(orderId);
        if (existing != null) {
            if (!Objects.equals(existing.getStatus(), AcceptanceStatus.DRAFT.getCode())) {
                throw new ServiceException("该订单已有验收单【" + existing.getCode() + "】且已提交，如需修改请先撤销");
            }
            // 幂等：已有草稿 → 同步缺失行（验收中途可能加了加单/换货/退货标记）
            return syncMissingItems(existing.getId());
        }
        return insertOrderAcceptance(order, buildOrderAcceptanceItems(orderId), order.getDeliveryDate());
    }

    /**
     * OA：同步订单维度验收草稿缺失行（《订单页一键验收链路设计》§4.3④/E2）：
     * 验收中途做了加单/换货/退货 → 按订单当前有效明细对齐：
     * 新行补插（默认实收=应送）、被标退货行应送/实收归 0（差异原因清空待重填）；
     * 不删旧行、不覆盖已录实收；末尾重算 totalAmount。
     */
    @Override
    @Transactional
    public Acceptance syncMissingItems(Long acceptanceId) {
        Acceptance acceptance = getExistAcceptance(acceptanceId);
        if (acceptance.getSaleOrderId() == null) {
            throw new ServiceException("仅订单维度验收单可同步订单明细");
        }
        if (!Objects.equals(acceptance.getStatus(), AcceptanceStatus.DRAFT.getCode())) {
            throw new ServiceException("仅草稿状态可同步明细");
        }
        List<SaleOrderDetail> orderDetails = saleOrderDetailMapper.selectValidByOrderIdForView(acceptance.getSaleOrderId());
        Map<Long, AcceptanceItem> existingByDetailId = acceptanceItemMapper.selectListByAcceptanceId(acceptanceId).stream()
                .filter(i -> i.getSaleOrderDetailId() != null)
                .collect(Collectors.toMap(AcceptanceItem::getSaleOrderDetailId, i -> i, (a, b) -> a));

        List<AcceptanceItem> toInsert = new ArrayList<>();
        for (SaleOrderDetail d : orderDetails) {
            AcceptanceItem existing = existingByDetailId.get(d.getId());
            boolean isReturned = d.getChangeType() != null && d.getChangeType() == 3;
            if (existing == null) {
                toInsert.add(buildOrderAcceptanceItem(d, existingByDetailId.size() + toInsert.size()));
            } else if (isReturned && existing.getDeliveredQuantity().compareTo(BigDecimal.ZERO) != 0) {
                // 已在单中的行后来被标退货：应送/实收归 0（同 D-055 语义），金额行重算在末尾统一做
                AcceptanceItem update = new AcceptanceItem();
                update.setId(existing.getId());
                update.setDeliveredQuantity(BigDecimal.ZERO);
                update.setActualQuantity(BigDecimal.ZERO);
                update.setDifferenceQuantity(BigDecimal.ZERO);
                update.setReasonType(null);
                update.setLossReason(null);
                update.setActualAmount(BigDecimal.ZERO);
                acceptanceItemMapper.updateAcceptanceItem(update);
                existing.setDeliveredQuantity(BigDecimal.ZERO);
                existing.setActualQuantity(BigDecimal.ZERO);
            }
        }
        if (!toInsert.isEmpty()) {
            for (AcceptanceItem item : toInsert) {
                item.setAcceptanceId(acceptanceId);
            }
            acceptanceItemMapper.insertAcceptanceItemBatch(toInsert);
        }
        return recalcOrderAcceptanceTotal(acceptanceId);
    }

    /**
     * OA：订单一键验收（《订单页一键验收链路设计》§4.5）：
     * 建单（如无）→ 同步缺失行 → 应用实收覆盖（可选，缺省全部实收=下单数量）→ 提交。
     * 提交内复用 {@link #submit} 的镜像回写与订单状态推进，任一环节失败整体回滚。
     */
    @Override
    @Transactional
    public Acceptance quickAccept(AcceptanceQuickAcceptDTO dto) {
        if (dto == null || dto.getOrderId() == null) {
            throw new ServiceException("订单ID不能为空");
        }
        SaleOrder order = saleOrderMapper.selectSaleOrderById(dto.getOrderId());
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        // 幂等：已提交单直接返回（双击/双端并发场景）；acceptanceId 归属校验防误传他单
        Acceptance existing = dto.getAcceptanceId() != null
                ? getExistAcceptance(dto.getAcceptanceId())
                : acceptanceMapper.selectBySaleOrder(dto.getOrderId());
        if (existing != null) {
            if (dto.getOrderId() != null && existing.getSaleOrderId() != null
                    && !existing.getSaleOrderId().equals(dto.getOrderId())) {
                throw new ServiceException("验收单【" + existing.getCode() + "】不属于该订单");
            }
            if (Objects.equals(existing.getStatus(), AcceptanceStatus.SUBMITTED.getCode())) {
                return existing;
            }
        }

        Acceptance acceptance = createByOrder(dto.getOrderId());
        if (dto.getAcceptDate() != null) {
            Acceptance dateUpdate = new Acceptance();
            dateUpdate.setId(acceptance.getId());
            dateUpdate.setAcceptDate(dto.getAcceptDate());
            dateUpdate.setRemark(dto.getRemark());
            acceptanceMapper.updateAcceptance(dateUpdate);
        }
        applyQuickAcceptOverrides(acceptance.getId(), dto.getItems());
        // 配送日期守卫：未到配送日的订单不可验收（草稿可提前建，提交必须在配送日当天及之后）
        if (order.getDeliveryDate() != null && order.getDeliveryDate().isAfter(LocalDate.now())) {
            throw new ServiceException("该订单配送日期（" + order.getDeliveryDate() + "）未到，送达后才能验收");
        }
        return submit(acceptance.getId());
    }

    /**
     * OA：一键验收实收覆盖应用（键=订单明细ID）：
     * 有覆盖行按覆盖值写实收/原因，无覆盖行保持建单默认（实收=应送）；
     * 覆盖后重算总额，供 submit 使用。
     */
    private void applyQuickAcceptOverrides(Long acceptanceId, List<AcceptanceQuickAcceptDTO.Item> overrides) {
        if (CollectionUtils.isEmpty(overrides)) {
            return;
        }
        Map<Long, AcceptanceQuickAcceptDTO.Item> overrideByDetailId = overrides.stream()
                .filter(i -> i.getSaleOrderDetailId() != null)
                .collect(Collectors.toMap(AcceptanceQuickAcceptDTO.Item::getSaleOrderDetailId, i -> i, (a, b) -> a));
        BigDecimal total = BigDecimal.ZERO;
        for (AcceptanceItem item : acceptanceItemMapper.selectListByAcceptanceId(acceptanceId)) {
            AcceptanceQuickAcceptDTO.Item override = overrideByDetailId.get(item.getSaleOrderDetailId());
            if (override == null || override.getActualQuantity() == null) {
                continue;
            }
            BigDecimal actual = override.getActualQuantity();
            if (actual.compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("实收数量不能为负：" + item.getProductName());
            }
            BigDecimal delivered = nvl(item.getDeliveredQuantity());
            BigDecimal diff = scale(actual.subtract(delivered));
            String reason = StringUtils.trimToNull(override.getLossReason());
            // OA 定稿：差异原因选填，仅自动记录短收/超收类型
            Integer reasonType = resolveReasonType(diff, actual, reason, item.getProductName());
            AcceptanceItem update = new AcceptanceItem();
            update.setId(item.getId());
            update.setActualQuantity(actual);
            update.setDifferenceQuantity(diff);
            update.setReasonType(reasonType);
            update.setLossReason(diff.compareTo(BigDecimal.ZERO) == 0 ? null : reason);
            update.setActualAmount(scale(nvl(item.getUnitPrice()).multiply(actual)));
            acceptanceItemMapper.updateAcceptanceItem(update);
        }
        // 重算总额（覆盖行与默认行一起）
        for (AcceptanceItem item : acceptanceItemMapper.selectListByAcceptanceId(acceptanceId)) {
            total = total.add(nvl(item.getActualAmount()));
        }
        Acceptance update = new Acceptance();
        update.setId(acceptanceId);
        update.setTotalAmount(total);
        acceptanceMapper.updateAcceptance(update);
    }

    /**
     * OA：订单维度验收行构建（应送行=订单明细行，含 D-055 标记）：
     * 退货行（change_type=3）应送=实收=0；加单行默认实收取订单 actual_num 镜像（现场已录），其余按应送兑底。
     */
    private List<AcceptanceItem> buildOrderAcceptanceItems(Long orderId) {
        List<SaleOrderDetail> orderDetails = saleOrderDetailMapper.selectValidByOrderIdForView(orderId);
        if (CollectionUtils.isEmpty(orderDetails)) {
            throw new ServiceException("该订单没有有效明细，无法生成验收单");
        }
        List<AcceptanceItem> items = new ArrayList<>();
        for (SaleOrderDetail d : orderDetails) {
            items.add(buildOrderAcceptanceItem(d, items.size()));
        }
        return items;
    }

    /** OA：单行构建（规则同上；与 createByCustomerDate 的行规则一致，仅数据源/定位键不同） */
    private AcceptanceItem buildOrderAcceptanceItem(SaleOrderDetail d, int sort) {
        boolean isReturned = d.getChangeType() != null && d.getChangeType() == 3;
        BigDecimal delivered = isReturned ? BigDecimal.ZERO : nvl(d.getNum());
        BigDecimal actual = isReturned ? BigDecimal.ZERO
                : (d.getActualNum() != null && d.getActualNum().compareTo(BigDecimal.ZERO) > 0
                        ? d.getActualNum() : delivered);
        AcceptanceItem item = new AcceptanceItem();
        item.setSaleOrderDetailId(d.getId());
        item.setCustomerDeptId(d.getCustomerDeptId());
        item.setSkuId(d.getSkuId());
        item.setProductName(d.getProductName());
        item.setProductSpec(d.getProductSpec());
        item.setProductUnit(d.getProductUnit());
        item.setDeliveredQuantity(delivered);
        item.setActualQuantity(actual);
        item.setUnitPrice(nvl(d.getProductPrice()));
        item.setDifferenceQuantity(actual.subtract(delivered));
        item.setSort(sort);
        item.setActualAmount(scale(item.getUnitPrice().multiply(actual)));
        return item;
    }

    /** OA：插入订单维度验收单主表（code/customer/date/dimension 冗余列）并批量写行 */
    private Acceptance insertOrderAcceptance(SaleOrder order, List<AcceptanceItem> items, LocalDate acceptDate) {
        BigDecimal total = BigDecimal.ZERO;
        for (AcceptanceItem item : items) {
            total = total.add(nvl(item.getActualAmount()));
        }
        Acceptance acceptance = new Acceptance();
        acceptance.setCode(bizCodeService.nextDailyCode("acceptance", "YS", 3));
        acceptance.setSaleOrderId(order.getId());
        acceptance.setCustomerId(order.getCustomerId());
        acceptance.setDeliveryPointId(order.getCustomerDeptId());
        acceptance.setDeliveryDate(order.getDeliveryDate());
        acceptance.setAcceptDate(acceptDate == null ? order.getDeliveryDate() : acceptDate);
        acceptance.setTotalAmount(total);
        acceptance.setStatus(AcceptanceStatus.DRAFT.getCode());
        acceptance.setCreateTime(DateUtils.getNowDate());
        acceptanceMapper.insertAcceptance(acceptance);

        for (AcceptanceItem item : items) {
            item.setAcceptanceId(acceptance.getId());
        }
        acceptanceItemMapper.insertAcceptanceItemBatch(items);
        return acceptance;
    }

    /** OA：重算订单维度验收单总额（同步缺失行后调用） */
    private Acceptance recalcOrderAcceptanceTotal(Long acceptanceId) {
        BigDecimal total = BigDecimal.ZERO;
        for (AcceptanceItem item : acceptanceItemMapper.selectListByAcceptanceId(acceptanceId)) {
            total = total.add(nvl(item.getActualAmount()));
        }
        Acceptance update = new Acceptance();
        update.setId(acceptanceId);
        update.setTotalAmount(total);
        acceptanceMapper.updateAcceptance(update);
        Acceptance acceptance = acceptanceMapper.selectAcceptanceById(acceptanceId);
        acceptance.setTotalAmount(total);
        return acceptance;
    }

    /**
     * 录入/修改验收单（仅草稿）：实收金额后端重算；
     * 差异原因口径（OA 定稿 2026-09-09）：原因选填，仅自动记录短收/超收类型，无差异清空原因。
     */
    @Override
    @Transactional
    public Acceptance updateDraft(AcceptanceUpdateDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new ServiceException("验收单ID不能为空");
        }
        Acceptance acceptance = getExistAcceptance(dto.getId());
        if (!Objects.equals(acceptance.getStatus(), AcceptanceStatus.DRAFT.getCode())) {
            throw new ServiceException("仅草稿状态可修改");
        }
        // 月结冻结校验（W0-3.1）：已月结客户该月验收单不可改
        checkNotSettled(acceptance.getCustomerId(), acceptance.getAcceptDate());
        if (CollectionUtils.isEmpty(dto.getItems())) {
            throw new ServiceException("验收明细不能为空");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (AcceptanceUpdateDTO.Item dtoItem : dto.getItems()) {
            if (dtoItem.getId() == null) {
                throw new ServiceException("验收明细行ID不能为空");
            }
            AcceptanceItem item = acceptanceItemMapper.selectAcceptanceItemById(dtoItem.getId());
            if (item == null || !Objects.equals(item.getAcceptanceId(), acceptance.getId())) {
                throw new ServiceException("验收明细不存在");
            }
            BigDecimal actual = dtoItem.getActualQuantity();
            if (actual == null) {
                throw new ServiceException("实收数量不能为空");
            }
            if (actual.compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("实收数量不能为负");
            }
            BigDecimal price = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            BigDecimal delivered = item.getDeliveredQuantity() == null ? BigDecimal.ZERO : item.getDeliveredQuantity();
            BigDecimal diff = scale(actual.subtract(delivered));
            String reason = StringUtils.trimToNull(dtoItem.getLossReason());
            Integer reasonType = resolveReasonType(diff, actual, reason, item.getProductName());

            AcceptanceItem update = new AcceptanceItem();
            update.setId(item.getId());
            update.setActualQuantity(actual);
            update.setDifferenceQuantity(diff);
            update.setReasonType(reasonType);
            // 无差异清空原因避免脏数据（XML 侧无条件写该列）
            update.setLossReason(diff.compareTo(BigDecimal.ZERO) == 0 ? null : reason);
            update.setActualAmount(scale(price.multiply(actual)));
            acceptanceItemMapper.updateAcceptanceItem(update);
            total = total.add(update.getActualAmount());
        }

        Acceptance update = new Acceptance();
        update.setId(acceptance.getId());
        update.setAcceptDate(dto.getAcceptDate());
        update.setRemark(dto.getRemark());
        update.setTotalAmount(total);
        update.setUpdateTime(DateUtils.getNowDate());
        acceptanceMapper.updateAcceptance(update);

        acceptance.setTotalAmount(total);
        acceptance.setAcceptDate(dto.getAcceptDate() == null ? acceptance.getAcceptDate() : dto.getAcceptDate());
        return acceptance;
    }

    /**
     * 差异原因类型与必填校验（OA 定稿 2026-09-09：原因改为选填，仅记录类型）：
     * <ul>
     *   <li>负差异 → 记短收类型(1)；</li>
     *   <li>正差异 = 超收 → 记超收类型(2)；</li>
     *   <li>无差异 → 不记录。</li>
     * </ul>
     * 原因不再强制（历史 D-013/G8/W0-2.6 必填规则废弃），有则存档供对账追溯。
     */
    private Integer resolveReasonType(BigDecimal diff, BigDecimal actual, String reason, String productName) {
        if (diff.compareTo(BigDecimal.ZERO) < 0) {
            return REASON_TYPE_SHORTFALL;
        }
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            return REASON_TYPE_OVERAGE;
        }
        return null;
    }

    /**
     * 提交验收单：状态→已提交，来源订单（IN 回写）→ ACCEPTED；
     * 同时按「点级实收×应送占比」把实收镜像同步到订单行 actual_*（只读镜像列，结算依据=验收单 total_amount）。
     */
    @Override
    @Transactional
    public Acceptance submit(Long id) {
        Acceptance acceptance = getExistAcceptance(id);
        if (!Objects.equals(acceptance.getStatus(), AcceptanceStatus.DRAFT.getCode())) {
            throw new ServiceException("仅草稿状态可提交");
        }
        Acceptance update = new Acceptance();
        update.setId(id);
        update.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        update.setUpdateTime(DateUtils.getNowDate());
        acceptanceMapper.updateAcceptance(update);

        // 仅回写本验收单对应送货单的来源订单（S14/G3：IN 子查询，替代同组推断，DESIGN.md §4.2）
        if (acceptance.getSaleOrderId() != null) {
            // OA 订单维度：应送行=单张订单明细，实收 1:1 回写镜像；订单 CONFIRMED→ACCEPTED
            syncActualMirrorFromOrderDetails(id);
            saleOrderMapper.updateStatusByIds(List.of(acceptance.getSaleOrderId()),
                    SaleOrderStatus.CONFIRMED.getCode(), SaleOrderStatus.ACCEPTED.getCode());
        } else if (acceptance.getDeliveryOrderId() != null) {
            saleOrderMapper.updateStatusByDeliveryId(
                    acceptance.getDeliveryOrderId(),
                    SaleOrderStatus.DELIVERED.getCode(),
                    SaleOrderStatus.ACCEPTED.getCode());
            syncActualMirror(id, acceptance.getDeliveryOrderId());
        } else {
            // D-055 点单验收（无送货单）：应送行=订单明细，实收 1:1 回写 actual_* 镜像；
            // 来源订单 CONFIRMED→ACCEPTED（验收后订单冻结，再要退补走退货单/新订单）
            syncActualMirrorFromOrderDetails(id);
            List<Long> orderIds = sourceOrderIdsFromItems(id);
            if (!orderIds.isEmpty()) {
                saleOrderMapper.updateStatusByIds(orderIds,
                        SaleOrderStatus.CONFIRMED.getCode(), SaleOrderStatus.ACCEPTED.getCode());
            }
        }
        acceptance.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        return acceptance;
    }

    /**
     * D-055 点单验收的 actual_* 镜像同步：验收行与订单明细一一对应（应送行即订单明细行），
     * 故无需占比分摊，直接按行回写实收数量/单价/金额与差异原因。
     */
    private void syncActualMirrorFromOrderDetails(Long acceptanceId) {
        for (AcceptanceItem item : CollectionUtils.emptyIfNull(acceptanceItemMapper.selectListByAcceptanceId(acceptanceId))) {
            if (item.getSaleOrderDetailId() == null) {
                continue;
            }
            SaleOrderDetail mirror = new SaleOrderDetail();
            mirror.setId(item.getSaleOrderDetailId());
            mirror.setActualNum(item.getActualQuantity());
            mirror.setActualPrice(item.getUnitPrice());
            mirror.setActualAmount(item.getActualAmount() != null
                    ? item.getActualAmount()
                    : scale(nvl(item.getUnitPrice()).multiply(nvl(item.getActualQuantity()))));
            mirror.setLossReason(item.getLossReason());
            saleOrderDetailMapper.updateActualBatch(mirror);
        }
    }

    /**
     * D-055 点单验收：由验收行携带的 sale_order_detail_id 反查去重后的来源订单ID集合
     * （一个订单只属一个配送点，因此回写面与一维一验口径天然对齐）。
     */
    private List<Long> sourceOrderIdsFromItems(Long acceptanceId) {
        List<Long> detailIds = CollectionUtils.emptyIfNull(acceptanceItemMapper.selectListByAcceptanceId(acceptanceId)).stream()
                .map(AcceptanceItem::getSaleOrderDetailId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (detailIds.isEmpty()) {
            return List.of();
        }
        return saleOrderDetailMapper.selectByIdIn(detailIds).stream()
                .map(SaleOrderDetail::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * actual_* 镜像同步（S14/T5，禁止业务直写）：对每条点级验收行，
     * 行内各来源订单行按应送占比分摊实收（行实收 = 分配量 × 实收/应送），单价锁来源行快照。
     * 历史单（无 source_item）跳过——镜像列允许为空，结算以验收单为准。
     */
    private void syncActualMirror(Long acceptanceId, Long deliveryOrderId) {
        List<AcceptanceItem> items = acceptanceItemMapper.selectListByAcceptanceId(acceptanceId);
        if (CollectionUtils.isEmpty(items)) {
            return;
        }
        List<DeliverySourceItem> sources = deliverySourceItemMapper.selectListByDeliveryId(deliveryOrderId);
        if (CollectionUtils.isEmpty(sources)) {
            return;
        }
        Map<Long, List<DeliverySourceItem>> byDetail = sources.stream()
                .filter(si -> si.getDeliveryDetailId() != null)
                .collect(Collectors.groupingBy(DeliverySourceItem::getDeliveryDetailId));
        for (AcceptanceItem item : items) {
            BigDecimal delivered = nvl(item.getDeliveredQuantity());
            BigDecimal actual = nvl(item.getActualQuantity());
            if (delivered.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal ratio = actual.divide(delivered, 6, RoundingMode.HALF_UP);
            for (DeliverySourceItem si : byDetail.getOrDefault(item.getDeliveryItemId(), List.of())) {
                BigDecimal lineActual = nvl(si.getAllocatedQuantity()).multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                BigDecimal linePrice = nvl(si.getUnitPrice());
                SaleOrderDetail mirror = new SaleOrderDetail();
                mirror.setId(si.getSaleOrderDetailId());
                mirror.setActualNum(lineActual);
                mirror.setActualPrice(linePrice);
                mirror.setActualAmount(scale(linePrice.multiply(lineActual)));
                saleOrderDetailMapper.updateActualBatch(mirror);
            }
        }
    }

    /**
     * 撤销验收（S14/T5，DESIGN.md §5.4/Q16/D-014）：已提交→草稿，原因必填；
     * 撤回前主表+明细完整快照落 t_acceptance_revoke_log；来源订单 ACCEPTED→DELIVERED（IN 回写）；
     * 清空来源订单行 actual_* 镜像（下次提交重新同步）；任一来源订单 SETTLED → 拒绝。
     */
    @Override
    @Transactional
    public Acceptance revoke(Long id, String reason) {
        if (StringUtils.isBlank(reason)) {
            throw new ServiceException("撤销原因不能为空");
        }
        Acceptance acceptance = getExistAcceptance(id);
        if (!Objects.equals(acceptance.getStatus(), AcceptanceStatus.SUBMITTED.getCode())) {
            throw new ServiceException("仅已提交的验收单可撤销");
        }
        // 月结冻结校验（W0-3.1）：已月结客户该月验收单不可撤销
        checkNotSettled(acceptance.getCustomerId(), acceptance.getAcceptDate());

        // 任一来源订单已结算 → 拒绝（结算依据链不可断）
        boolean pointBased = acceptance.getDeliveryOrderId() == null;
        List<Long> sourceOrderIds;
        if (acceptance.getSaleOrderId() != null) {
            // OA 订单维度：来源订单即单据上的 sale_order_id
            sourceOrderIds = List.of(acceptance.getSaleOrderId());
        } else if (pointBased) {
            sourceOrderIds = sourceOrderIdsFromItems(id);
        } else {
            sourceOrderIds = deliverySourceItemMapper.selectListByDeliveryId(acceptance.getDeliveryOrderId())
                    .stream()
                    .map(DeliverySourceItem::getSaleOrderId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }
        if (CollectionUtils.isNotEmpty(sourceOrderIds)) {
            String settledCodes = saleOrderMapper.selectSaleOrderByIdIn(sourceOrderIds).stream()
                    .filter(o -> SaleOrderStatus.SETTLED.getCode().equals(o.getStatus()))
                    .map(SaleOrder::getCode)
                    .collect(Collectors.joining("、"));
            if (StringUtils.isNotBlank(settledCodes)) {
                throw new ServiceException("来源订单已结算，禁止撤销验收：" + settledCodes);
            }
        }

        // 1) 撤回前完整审计快照（主表+明细）
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("acceptance", acceptance);
        snapshot.put("items", acceptanceItemMapper.selectListByAcceptanceId(id));
        AcceptanceRevokeLog revokeLog = new AcceptanceRevokeLog();
        revokeLog.setAcceptanceId(id);
        revokeLog.setSnapshotJson(JSON.toJSONString(snapshot));
        revokeLog.setReason(reason.trim());
        revokeLog.setRevokedBy(resolveOperator());
        revokeLog.setRevokedTime(DateUtils.getNowDate());
        acceptanceRevokeLogMapper.insertAcceptanceRevokeLog(revokeLog);

        // 2) 验收单回草稿 + 记撤销三要素（点级明细行保留，重新编辑/提交）
        Acceptance update = new Acceptance();
        update.setId(id);
        update.setStatus(AcceptanceStatus.DRAFT.getCode());
        update.setRevokeReason(revokeLog.getReason());
        update.setRevokedBy(revokeLog.getRevokedBy());
        update.setRevokedTime(revokeLog.getRevokedTime());
        update.setUpdateTime(DateUtils.getNowDate());
        acceptanceMapper.updateAcceptance(update);

        // 3) 来源订单状态回退：历史送货单 ACCEPTED→DELIVERED；D-055 点单/OA 订单维度验收 ACCEPTED→CONFIRMED（回到未验收可继续验收）
        if (pointBased) {
            if (!sourceOrderIds.isEmpty()) {
                saleOrderMapper.updateStatusByIds(sourceOrderIds,
                        SaleOrderStatus.ACCEPTED.getCode(), SaleOrderStatus.CONFIRMED.getCode());
            }
        } else {
            saleOrderMapper.updateStatusByDeliveryId(
                    acceptance.getDeliveryOrderId(),
                    SaleOrderStatus.ACCEPTED.getCode(),
                    SaleOrderStatus.DELIVERED.getCode());
        }

        // 4) 清空来源订单行 actual_* 镜像（验收回到草稿，镜像待重新提交后同步）
        sourceOrderIds.forEach(orderId -> saleOrderDetailMapper.clearActualByOrderId(orderId));

        log.info("[acceptance revoke] 验收单 {} 已撤销，原因：{}", acceptance.getCode(), revokeLog.getReason());
        acceptance.setStatus(AcceptanceStatus.DRAFT.getCode());
        acceptance.setRevokeReason(revokeLog.getReason());
        acceptance.setRevokedBy(revokeLog.getRevokedBy());
        acceptance.setRevokedTime(revokeLog.getRevokedTime());
        return acceptance;
    }

    @Override
    @Transactional
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (Long id : ids) {
            Acceptance exist = acceptanceMapper.selectAcceptanceById(id);
            if (exist == null) {
                continue;
            }
            if (!Objects.equals(exist.getStatus(), AcceptanceStatus.DRAFT.getCode())) {
                throw new ServiceException("仅草稿状态可删除：" + exist.getCode());
            }
            acceptanceItemMapper.deleteAcceptanceItemByAcceptanceId(id);
        }
        return acceptanceMapper.deleteAcceptanceByIds(ids);
    }

    private Acceptance getExistAcceptance(Long id) {
        if (id == null) {
            throw new ServiceException("验收单ID不能为空");
        }
        Acceptance exist = acceptanceMapper.selectAcceptanceById(id);
        if (exist == null) {
            throw new ServiceException("验收单不存在");
        }
        return exist;
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

    /**
     * 月结冻结校验（W0-3.1）：按「客户 + 结算月」判定，已月结则拒绝修改/撤销验收单（纠错走下月调整单）
     */
    private void checkNotSettled(Long customerId, LocalDate date) {
        if (customerId == null || date == null) {
            return;
        }
        String month = YearMonth.from(date).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        MonthSettlement s = monthSettlementMapper.selectByCustomerAndMonth(customerId, month);
        if (s != null && Integer.valueOf(1).equals(s.getStatus())) {
            throw new ServiceException("客户该月（" + month + "）已月结，验收单已冻结，请使用下月调整单");
        }
    }
}

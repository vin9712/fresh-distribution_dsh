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
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliverySourceItemMapper;
import com.lin.distribution.mapper.ReturnItemMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
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
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderDetailMapper saleOrderDetailMapper;
    private final ReturnItemMapper returnItemMapper;
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
        return items;
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
     * 「去验收」定位（S14 §6.1/§八）：
     * ① 新模型走 source_item 有效分配反查送货单；② 历史单回退送货明细行 order_id；
     * ③ 排除已作废单；④ 一单分布在多张有效单（补充单）时优先取已建验收单的最新一张，
     * 都没有则定位最新单，前端带 deliveryId 引导创建验收草稿。
     */
    @Override
    public AcceptanceByOrderVO locateBySaleOrder(Long orderId) {
        AcceptanceByOrderVO vo = new AcceptanceByOrderVO();
        vo.setOrderId(orderId);
        vo.setHasAcceptance(false);

        // ① 新模型：source_item 有效分配（is_deleted=0，作废释放后不会命中）
        DeliverySourceItem sourceQuery = new DeliverySourceItem();
        sourceQuery.setSaleOrderId(orderId);
        List<Long> deliveryIds = deliverySourceItemMapper.selectDeliverySourceItemList(sourceQuery)
                .stream()
                .map(DeliverySourceItem::getDeliveryId)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // ② 历史单回退：S14 前生成的送货明细行 order_id（无台账）
        if (deliveryIds.isEmpty()) {
            deliveryIds = deliveryOrderDetailMapper.selectListByOrderIdIn(List.of(orderId))
                    .stream()
                    .map(DeliveryOrderDetail::getDeliveryId)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (deliveryIds.isEmpty()) {
            return vo;
        }

        // ③ 过滤已作废（历史单作废无台账软删痕迹，必须按单据状态排除）
        List<DeliveryOrder> validDeliveries = deliveryOrderMapper.selectListByIds(deliveryIds)
                .stream()
                .filter(d -> !Objects.equals(d.getStatus(), DeliveryOrderStatus.VOIDED.getCode()))
                .sorted(Comparator.comparing(DeliveryOrder::getId))
                .collect(Collectors.toList());
        if (validDeliveries.isEmpty()) {
            return vo;
        }
        vo.setDeliveryIds(validDeliveries.stream().map(DeliveryOrder::getId).collect(Collectors.toList()));

        // ④ 从最新单向前找已有验收单的单；都没有则定位最新单引导创建草稿
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
     * 录入/修改验收单（仅草稿）：实收金额后端重算；
     * 双向差异必填原因（D-013/G8）：负差异→reason_type=1(短收)，正差异→reason_type=2(超收)，无差异清空原因。
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
            Integer reasonType = resolveReasonType(diff, reason, item.getProductName());

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
     * 差异原因类型与必填校验（双向，D-013/G8）：
     * 负差异=短收(1，字典 acceptance_shortfall_reason)；正差异=超收(2，字典 acceptance_overage_reason)；无差异不要求。
     */
    private Integer resolveReasonType(BigDecimal diff, String reason, String productName) {
        if (diff.compareTo(BigDecimal.ZERO) < 0) {
            if (StringUtils.isBlank(reason)) {
                throw new ServiceException("短收差异必须填写原因：" + productName);
            }
            return REASON_TYPE_SHORTFALL;
        }
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            if (StringUtils.isBlank(reason)) {
                throw new ServiceException("超收差异必须填写原因：" + productName);
            }
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
        saleOrderMapper.updateStatusByDeliveryId(
                acceptance.getDeliveryOrderId(),
                SaleOrderStatus.DELIVERED.getCode(),
                SaleOrderStatus.ACCEPTED.getCode());

        syncActualMirror(id, acceptance.getDeliveryOrderId());
        acceptance.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        return acceptance;
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

        // 任一来源订单已结算 → 拒绝（结算依据链不可断）
        List<Long> sourceOrderIds = deliverySourceItemMapper.selectListByDeliveryId(acceptance.getDeliveryOrderId())
                .stream()
                .map(DeliverySourceItem::getSaleOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
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

        // 3) 来源订单 ACCEPTED→DELIVERED（IN 回写）
        saleOrderMapper.updateStatusByDeliveryId(
                acceptance.getDeliveryOrderId(),
                SaleOrderStatus.ACCEPTED.getCode(),
                SaleOrderStatus.DELIVERED.getCode());

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
}

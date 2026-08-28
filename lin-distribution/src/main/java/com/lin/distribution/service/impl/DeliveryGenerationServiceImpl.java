package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.DeliveryGenerateTrigger;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.DeliveryScopeType;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.DeliveryBatch;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.DeliverySourceItem;
import com.lin.distribution.domain.JobRunLog;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.DeliveryByOrdersDTO;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.DeliveryBatchMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliverySourceItemMapper;
import com.lin.distribution.mapper.JobRunLogMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.DeliveryGenerationService;
import com.lin.distribution.vo.GenerateResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 送货单统一生成服务实现（S14/T3，DESIGN.md §5.1）
 *
 * <p>核心口径：
 * <ul>
 *   <li>遗漏订单 O_missed = 该客户当日 CONFIRMED 且未进入任何有效送货单
 *       （新模型查 t_delivery_source_item 有效分配；S14 前历史单查 t_delivery_order_detail.order_id 兼容判定）；</li>
 *   <li>三态分支：无既有单→正常生成（doc_kind=0）；V 全部未打印→作废重建（D-022，覆盖全部已确认订单）；
 *       V 含已打印/已送达→补充单（D-023，doc_kind=1，原单不动）；</li>
 *   <li>组单策略取批次快照（D-016 批次期内策略不变）；merge_same_item 按五元组
 *       sku+品名快照+单位+规格+单价 合并，不同价必拆行（D-024），临时商品（sku_id 空）五元组全比对；</li>
 *   <li>聚合在 Java 侧完成：明细行插入后立即按「订单行→送货行」映射批量落 t_delivery_source_item；</li>
 *   <li>单客户一个事务（generateForDate 内经 TransactionTemplate 逐客户隔离），任一步失败整体回滚。</li>
 * </ul></p>
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryGenerationServiceImpl implements DeliveryGenerationService {

    /** 作废重建分支的固定作废原因（D-022，非人工作废，T4 的手工作废原因走字典） */
    private static final String REBUILD_VOID_REASON = "补单重建（未打印原单自动作废后重建）";

    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    private final DeliverySourceItemMapper deliverySourceItemMapper;
    private final DeliveryBatchMapper deliveryBatchMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderDetailMapper saleOrderDetailMapper;
    private final CustomerMapper customerMapper;
    private final JobRunLogMapper jobRunLogMapper;
    private final BizCodeService bizCodeService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 幂等生成某配送日期全部客户的批次+送货单（定时窗口入口/旧按日期入口委托）
     */
    @Override
    public GenerateResultVO generateForDate(LocalDate deliveryDate, DeliveryGenerateTrigger trigger) {
        if (deliveryDate == null) {
            throw new ServiceException("配送日期不能为空");
        }
        DeliveryGenerateTrigger safeTrigger = trigger == null ? DeliveryGenerateTrigger.MANUAL : trigger;
        GenerateResultVO result = new GenerateResultVO();

        // 全日期遗漏订单（customerId 传 null = 不限客户）；无遗漏即幂等返回
        List<SaleOrder> missedOrders = saleOrderMapper.selectMissedConfirmedOrders(null, deliveryDate);
        if (CollectionUtils.isEmpty(missedOrders)) {
            result.getSkippedReasons().add("配送日期 " + deliveryDate + " 无遗漏订单（幂等跳过）");
            writeJobRunLogIfScheduled(safeTrigger, deliveryDate, JobRunLog.STATUS_SUCCESS, "无遗漏订单", 0);
            return result;
        }

        // 逐客户隔离事务：单客户失败回滚自身并继续其他客户（部分失败 → job_run_log 状态=2）
        Map<Long, List<SaleOrder>> byCustomer = missedOrders.stream()
                .collect(Collectors.groupingBy(SaleOrder::getCustomerId, LinkedHashMap::new, Collectors.toList()));
        String operator = resolveOperator();
        List<String> failures = new ArrayList<>();
        int warningCount = 0;
        for (Map.Entry<Long, List<SaleOrder>> entry : byCustomer.entrySet()) {
            Long customerId = entry.getKey();
            try {
                GenerateResultVO part = transactionTemplate.execute(
                        status -> doGenerateForCustomer(customerId, deliveryDate, safeTrigger, operator));
                mergeResult(result, part);
            } catch (Exception e) {
                log.error("[delivery generate] customer {} date {} failed", customerId, deliveryDate, e);
                failures.add("客户ID=" + customerId + ": " + e.getMessage());
                warningCount += entry.getValue().size();
            }
        }

        if (safeTrigger == DeliveryGenerateTrigger.SCHEDULED) {
            if (failures.isEmpty()) {
                writeJobRunLog(deliveryDate, JobRunLog.STATUS_SUCCESS,
                        "成功生成 " + result.getCreatedOrders().size() + " 张送货单", 0);
            } else if (failures.size() >= byCustomer.size()) {
                writeJobRunLog(deliveryDate, JobRunLog.STATUS_FAILED,
                        StringUtils.abbreviate("全部客户生成失败：" + String.join("；", failures), 500), warningCount);
            } else {
                writeJobRunLog(deliveryDate, JobRunLog.STATUS_PARTIAL,
                        StringUtils.abbreviate("部分客户生成失败：" + String.join("；", failures), 500), warningCount);
            }
        }
        return result;
    }

    /**
     * 手工按客户+日期补齐全部遗漏订单（D-021 主路径：录单页「本客户订单已录完」/订单列表按客户）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerateResultVO generateForCustomer(Long customerId, LocalDate deliveryDate) {
        if (customerId == null) {
            throw new ServiceException("客户不能为空");
        }
        if (deliveryDate == null) {
            throw new ServiceException("配送日期不能为空");
        }
        return doGenerateForCustomer(customerId, deliveryDate, DeliveryGenerateTrigger.MANUAL, resolveOperator());
    }

    /**
     * 按勾选订单生成（旧 /generate-by-orders 入口兼容委托，D-025 语义=按客户+日期补齐全部遗漏）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerateResultVO generateForOrders(DeliveryByOrdersDTO dto) {
        if (dto == null || CollectionUtils.isEmpty(dto.getOrderIds())) {
            throw new ServiceException("请选择要生成送货单的订单");
        }
        List<Long> orderIds = dto.getOrderIds().stream().distinct().collect(Collectors.toList());
        List<SaleOrder> orders = saleOrderMapper.selectSaleOrderByIdIn(orderIds);
        if (orders.size() != orderIds.size()) {
            throw new ServiceException("部分订单不存在或已删除，请刷新列表后重试");
        }

        // 定位 客户+配送日期 分组（未传日期时逐单取自身配送日期；已进单订单由幂等判定自然排除）
        Map<String, List<SaleOrder>> groups = orders.stream().collect(Collectors.groupingBy(
                o -> o.getCustomerId() + ":" + (dto.getDeliveryDate() != null ? dto.getDeliveryDate() : o.getDeliveryDate()),
                LinkedHashMap::new, Collectors.toList()));
        GenerateResultVO result = new GenerateResultVO();
        for (List<SaleOrder> group : groups.values()) {
            SaleOrder first = group.get(0);
            LocalDate deliveryDate = dto.getDeliveryDate() != null ? dto.getDeliveryDate() : first.getDeliveryDate();
            if (deliveryDate == null) {
                throw new ServiceException("订单 " + first.getCode() + " 未设置预计配送日期，请先补全后再生成送货单");
            }
            mergeResult(result, doGenerateForCustomer(first.getCustomerId(), deliveryDate,
                    DeliveryGenerateTrigger.MANUAL, resolveOperator()));
        }
        return result;
    }

    /**
     * 单客户生成主体（调用方保证事务上下文：generateForCustomer 经 @Transactional，
     * generateForDate 经 TransactionTemplate 逐客户隔离）
     */
    private GenerateResultVO doGenerateForCustomer(Long customerId, LocalDate deliveryDate,
                                                   DeliveryGenerateTrigger trigger, String operator) {
        GenerateResultVO result = new GenerateResultVO();

        // 1. 遗漏订单集 O_missed
        List<SaleOrder> missedOrders = saleOrderMapper.selectMissedConfirmedOrders(customerId, deliveryDate);

        // 2. 幂等：无遗漏直接返回（不建批次、不碰既有单据）
        if (CollectionUtils.isEmpty(missedOrders)) {
            result.getSkippedReasons().add("客户ID=" + customerId + " " + deliveryDate + " 无遗漏订单（幂等跳过）");
            return result;
        }

        // 3. UPSERT 批次：已存在则复用策略快照（D-016 批次期内策略不变），不存在按客户当前配置快照
        DeliveryBatch batch = upsertBatch(customerId, deliveryDate, operator);

        // 4. 既有外部单集合 V = 该客户当日未作废送货单（含 batch_id 为空的历史单，平滑迁移）
        List<DeliveryOrder> activeOrders = deliveryOrderMapper.selectActiveByCustomerAndDate(customerId, deliveryDate);
        List<SaleOrder> buildOrders = missedOrders;
        int docKind = 0;
        boolean rebuild = false;
        Map<Long, DeliveryOrder> voidedByPoint = new HashMap<>();
        DeliveryOrder singleVoided = null;
        if (CollectionUtils.isNotEmpty(activeOrders)) {
            boolean anyPrintedOrDelivered = activeOrders.stream()
                    .anyMatch(v -> !DeliveryOrderStatus.PENDING.getCode().equals(v.getStatus()));
            if (anyPrintedOrDelivered) {
                // 【补充单分支 D-023】原单不动，仅用遗漏订单生成 doc_kind=1 补充送货单
                docKind = 1;
            } else {
                // 【作废重建分支 D-022】未打印原单整体作废（释放来源分配），按全部已确认订单重建
                for (DeliveryOrder oldOrder : activeOrders) {
                    voidForRebuild(oldOrder, operator);
                    voidedByPoint.put(oldOrder.getDeliveryPointId(), oldOrder);
                }
                singleVoided = activeOrders.size() == 1 ? activeOrders.get(0) : null;
                rebuild = true;
                buildOrders = saleOrderMapper.selectConfirmedByCustomerAndDate(customerId, deliveryDate);
            }
        }

        if (CollectionUtils.isEmpty(buildOrders)) {
            result.getSkippedReasons().add("客户ID=" + customerId + " " + deliveryDate + " 无可生成订单");
            return result;
        }

        // 5-7. 建单 + 聚合明细 + 来源分配（任何一步失败整事务回滚，批次不留半截数据）
        List<DeliveryOrder> createdOrders = createDeliveryOrders(batch, buildOrders, docKind,
                rebuild, voidedByPoint, singleVoided, operator);

        result.setBatchId(batch.getId());
        result.setCreatedOrders(createdOrders);
        result.setMissedOrders(buildOrders);
        return result;
    }

    /**
     * 批次 UPSERT：锁行防并发；已存在复用快照（D-016），不存在按客户当前配置快照落库。
     * 极端并发下双方同时判空插入，后者撞 unq_customer_date 唯一键整体回滚，重试即见既有批次。
     */
    private DeliveryBatch upsertBatch(Long customerId, LocalDate deliveryDate, String operator) {
        DeliveryBatch batch = deliveryBatchMapper.selectByCustomerAndDateForUpdate(customerId, deliveryDate);
        if (batch != null) {
            return batch;
        }
        Customer customer = customerMapper.selectCustomerById(customerId);
        if (customer == null) {
            throw new ServiceException("客户不存在（ID=" + customerId + "），无法生成送货单");
        }
        DeliveryBatch created = DeliveryBatch.builder()
                .customerId(customerId)
                .deliveryDate(deliveryDate)
                .scopeType(DeliveryScopeType.normalize(customer.getDocScopeType()))
                .mergeSameItem(customer.getDocMergeSameItem() == null ? Boolean.TRUE : customer.getDocMergeSameItem())
                .status(0)
                .version(0)
                .isDeleted(Boolean.FALSE)
                .build();
        created.setCreateTime(DateUtils.getNowDate());
        created.setCreateBy(operator);
        deliveryBatchMapper.insertDeliveryBatch(created);
        return created;
    }

    /**
     * 作废重建：置 VOIDED + 原因/人/时间，来源分配软删释放（is_deleted 参与唯一键，重建不撞键）
     */
    private void voidForRebuild(DeliveryOrder oldOrder, String operator) {
        oldOrder.setStatus(DeliveryOrderStatus.VOIDED.getCode());
        oldOrder.setVoidReason(REBUILD_VOID_REASON);
        oldOrder.setVoidBy(operator);
        oldOrder.setVoidTime(DateUtils.getNowDate());
        oldOrder.setUpdateTime(DateUtils.getNowDate());
        deliveryOrderMapper.updateDeliveryOrder(oldOrder);
        deliverySourceItemMapper.deleteByDeliveryId(oldOrder.getId());
    }

    /**
     * 按 batch.scope_type 分组建单 + Java 侧聚合明细 + 落来源分配台账
     */
    private List<DeliveryOrder> createDeliveryOrders(DeliveryBatch batch, List<SaleOrder> orders, int docKind,
                                                     boolean rebuild, Map<Long, DeliveryOrder> voidedByPoint,
                                                     DeliveryOrder singleVoided, String operator) {
        List<Long> orderIds = orders.stream().map(SaleOrder::getId).collect(Collectors.toList());
        // 订单行原始明细（不聚合：聚合在 Java 侧，同时保留 行→单 映射供 source_item 使用）
        List<SaleOrderDetail> rows = saleOrderDetailMapper.selectValidByOrderIdIn(orderIds);
        if (CollectionUtils.isEmpty(rows)) {
            throw new ServiceException("所选订单没有有效明细，无法生成送货单");
        }
        Map<Long, SaleOrder> orderMap = orders.stream()
                .collect(Collectors.toMap(SaleOrder::getId, o -> o, (a, b) -> a));
        // 配送点兜底：明细行 dept 为空时回填订单 dept，保证 B/C 类分组与 source_item 归属可用
        rows.forEach(row -> row.setCustomerDeptId(resolveDeptId(row, orderMap)));

        boolean customerDate = DeliveryScopeType.isCustomerDate(batch.getScopeType());
        // 分组：A类总单全客户一张（delivery_point_id=NULL）；B/C类每个配送点一张
        Map<Long, List<SaleOrderDetail>> groups = new LinkedHashMap<>();
        if (customerDate) {
            groups.put(null, rows);
        } else {
            groups.putAll(rows.stream().collect(Collectors.groupingBy(
                    SaleOrderDetail::getCustomerDeptId, LinkedHashMap::new, Collectors.toList())));
        }

        Date now = DateUtils.getNowDate();
        LocalDateTime nowLdt = LocalDateTime.ofInstant(now.toInstant(), ZoneId.systemDefault());
        List<DeliveryOrder> created = new ArrayList<>();
        for (Map.Entry<Long, List<SaleOrderDetail>> entry : groups.entrySet()) {
            Long pointId = entry.getKey();
            DeliveryOrder deliveryOrder = DeliveryOrder.builder()
                    .customerId(batch.getCustomerId())
                    .deliveryPointId(pointId)
                    .batchId(batch.getId())
                    .scopeType(batch.getScopeType())
                    .docKind(docKind)
                    .predecessorId(resolvePredecessor(customerDate, pointId, rebuild, voidedByPoint, singleVoided))
                    .code(bizCodeService.nextDailyCode("deliveryOrder", "HS", 3))
                    .status(DeliveryOrderStatus.PENDING.getCode())
                    .printCount(0)
                    .deliveryDate(batch.getDeliveryDate())
                    .isDeleted(Boolean.FALSE)
                    .version(0)
                    .build();
            deliveryOrder.setCreateTime(now);
            deliveryOrder.setCreateBy(operator);
            deliveryOrderMapper.insertDeliveryOrder(deliveryOrder);

            for (MergedDetail merged : mergeRows(entry.getValue(), Boolean.TRUE.equals(batch.getMergeSameItem()))) {
                DeliveryOrderDetail detail = DeliveryOrderDetail.builder()
                        .deliveryId(deliveryOrder.getId())
                        .customerId(batch.getCustomerId())
                        // A类总单 dept 维度仅留 source_item（设计 §5.1 步骤5）；B/C类保留分组点
                        .customerDeptId(customerDate ? null : merged.getCustomerDeptId())
                        .orderCode(merged.getOrderCode() == null ? "" : merged.getOrderCode())
                        .skuId(merged.getSkuId())
                        .productName(merged.getProductName())
                        .productUnit(merged.getUnit())
                        .productSpec(merged.getSpec())
                        .num(merged.getNum())
                        .price(merged.getPrice())
                        .amount(merged.getPrice().multiply(merged.getNum()))
                        .isPrint(Boolean.FALSE)
                        .isDeleted(Boolean.FALSE)
                        .version(0)
                        .build();
                detail.setCreateTime(now);
                deliveryOrderDetailMapper.insertDeliveryOrderDetail(detail);

                // 明细落库后立即写来源分配（delivery_detail_id 依赖明细回填主键）
                List<DeliverySourceItem> sourceItems = merged.getSources().stream().map(row -> {
                    DeliverySourceItem item = new DeliverySourceItem();
                    item.setDeliveryId(deliveryOrder.getId());
                    item.setDeliveryDetailId(detail.getId());
                    item.setSaleOrderId(row.getOrderId());
                    item.setSaleOrderDetailId(row.getId());
                    item.setCustomerDeptId(row.getCustomerDeptId());
                    item.setSkuId(row.getSkuId());
                    item.setProductName(row.getProductName());
                    item.setAllocatedQuantity(nvl(row.getNum()));
                    item.setUnitPrice(nvl(row.getProductPrice()));
                    item.setIsDeleted(Boolean.FALSE);
                    item.setCreateTime(nowLdt);
                    item.setCreateBy(operator);
                    return item;
                }).collect(Collectors.toList());
                deliverySourceItemMapper.batchInsertDeliverySourceItem(sourceItems);
            }
            created.add(deliveryOrder);
        }
        return created;
    }

    /**
     * 明细聚合（蓝图 W0-2.3 + S14/DESIGN.md §5.1）：
     * <ul>
     *   <li>merge=false 一订单行一行（保持订单行原始顺序）；</li>
     *   <li>merge=true 按五元组合并（D-024 不同价必拆行；临时商品 sku 空时其余四元全比对防误并）；</li>
     *   <li>合单排序（蓝图 W0-2.3）：以同一配送点/总单下商品行数最多的订单为基准保持其行顺序，
     *       其余订单按下单顺序（orderId 升序）补充未出现商品；</li>
     *   <li>合并产生的多来源行订单号置空（订单归属维度留 source_item 对照列，G1 修复口径）。</li>
     * </ul>
     */
    private List<MergedDetail> mergeRows(List<SaleOrderDetail> rows, boolean mergeSameItem) {
        List<MergedDetail> details = new ArrayList<>();
        if (!mergeSameItem) {
            for (SaleOrderDetail row : rows) {
                details.add(MergedDetail.of(row));
            }
            return details;
        }

        // 合单排序前提：按订单分组（保持行内自然顺序），以行数最多订单为基准，并列取先出现者
        Map<Long, List<SaleOrderDetail>> byOrder = new LinkedHashMap<>();
        for (SaleOrderDetail row : rows) {
            byOrder.computeIfAbsent(row.getOrderId(), k -> new ArrayList<>()).add(row);
        }
        Long baseOrderId = null;
        int maxSize = -1;
        for (Map.Entry<Long, List<SaleOrderDetail>> e : byOrder.entrySet()) {
            if (e.getValue().size() > maxSize) {
                maxSize = e.getValue().size();
                baseOrderId = e.getKey();
            }
        }

        Map<String, MergedDetail> keyed = new LinkedHashMap<>();
        for (Long orderId : orderedGroupIds(byOrder, baseOrderId)) {
            for (SaleOrderDetail row : byOrder.get(orderId)) {
                MergedDetail merged = keyed.get(mergeKey(row));
                if (merged == null) {
                    merged = MergedDetail.of(row);
                    keyed.put(mergeKey(row), merged);
                    details.add(merged);
                } else {
                    merged.setNum(merged.getNum().add(nvl(row.getNum())));
                    merged.getSources().add(row);
                    // 合并行不再对应唯一订单行：订单号置空，溯源走 source_item
                    merged.setOrderCode(null);
                }
            }
        }
        return details;
    }

    /**
     * 合单排序组序：基准订单优先，其余订单按下单顺序（orderId 升序）排列
     */
    private List<Long> orderedGroupIds(Map<Long, List<SaleOrderDetail>> byOrder, Long baseOrderId) {
        List<Long> groupIds = new ArrayList<>();
        if (baseOrderId != null) {
            groupIds.add(baseOrderId);
        }
        byOrder.keySet().stream()
                .filter(id -> !id.equals(baseOrderId))
                .sorted()
                .forEach(groupIds::add);
        return groupIds;
    }

    /**
     * 五元组合并键：sku + 品名快照 + 单位 + 规格 + 单价（价格去尾零比较，2.0 与 2.00 视为同价）；
     * 临时商品 sku_id 为空时由其余四元全比对，防同名不同规格误并
     */
    private String mergeKey(SaleOrderDetail row) {
        return (row.getSkuId() == null ? "_" : row.getSkuId())
                + "|" + StringUtils.defaultString(row.getProductName())
                + "|" + StringUtils.defaultString(row.getProductUnit())
                + "|" + StringUtils.defaultString(row.getProductSpec())
                + "|" + nvl(row.getProductPrice()).stripTrailingZeros().toPlainString();
    }

    /**
     * predecessor 链：重建分支下，B/C类按配送点匹配被作废原单；A类总单仅唯一原单时可挂链，
     * 多张原单合一张总单时链头不唯一则不挂（链路靠作废单 void_reason 仍可追溯）
     */
    private Long resolvePredecessor(boolean customerDate, Long pointId, boolean rebuild,
                                    Map<Long, DeliveryOrder> voidedByPoint, DeliveryOrder singleVoided) {
        if (!rebuild) {
            return null;
        }
        if (customerDate) {
            return singleVoided == null ? null : singleVoided.getId();
        }
        DeliveryOrder byPoint = voidedByPoint.get(pointId);
        return byPoint == null ? null : byPoint.getId();
    }

    private Long resolveDeptId(SaleOrderDetail row, Map<Long, SaleOrder> orderMap) {
        if (row.getCustomerDeptId() != null) {
            return row.getCustomerDeptId();
        }
        SaleOrder order = orderMap.get(row.getOrderId());
        return order == null ? null : order.getCustomerDeptId();
    }

    private void mergeResult(GenerateResultVO target, GenerateResultVO part) {
        if (part == null) {
            return;
        }
        if (target.getBatchId() == null) {
            target.setBatchId(part.getBatchId());
        }
        target.getCreatedOrders().addAll(part.getCreatedOrders());
        target.getSkippedReasons().addAll(part.getSkippedReasons());
        target.getMissedOrders().addAll(part.getMissedOrders());
    }

    private void writeJobRunLogIfScheduled(DeliveryGenerateTrigger trigger, LocalDate bizDate, int status,
                                           String message, int warningCount) {
        if (trigger == DeliveryGenerateTrigger.SCHEDULED) {
            writeJobRunLog(bizDate, status, message, warningCount);
        }
    }

    /**
     * 写定时任务运行记录（Q36/D-037 工作台告警数据源）；仅 SCHEDULED 触发写入，
     * 手工路径由 @Log 操作日志覆盖。写入失败不阻断业务。
     */
    private void writeJobRunLog(LocalDate bizDate, int status, String message, int warningCount) {
        try {
            JobRunLog jobRunLog = new JobRunLog();
            jobRunLog.setJobName(JOB_NAME_DELIVERY_GENERATE);
            jobRunLog.setBizDate(bizDate);
            jobRunLog.setStatus(status);
            jobRunLog.setMessage(message);
            jobRunLog.setWarningCount(warningCount);
            jobRunLog.setRunTime(DateUtils.getNowDate());
            jobRunLogMapper.insertJobRunLog(jobRunLog);
        } catch (Exception e) {
            log.error("[delivery generate] write job run log failed, date:{}", bizDate, e);
        }
    }

    /**
     * 操作人：定时窗口无登录上下文回落 system
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

    /**
     * 聚合明细行（Java 侧合并单元）：保留五元组快照与来源行集合
     */
    private static class MergedDetail {
        private Long skuId;
        private String productName;
        private String unit;
        private String spec;
        private BigDecimal price;
        private BigDecimal num;
        /** 未合并（单来源）行保留订单号；合并行置空（溯源走 source_item 对照列） */
        private String orderCode;
        /** A类在明细落库时置空（dept 维度仅留 source_item）；B/C类保留分组点 */
        private Long customerDeptId;
        private List<SaleOrderDetail> sources = new ArrayList<>();

        static MergedDetail of(SaleOrderDetail row) {
            MergedDetail merged = new MergedDetail();
            merged.skuId = row.getSkuId();
            merged.productName = row.getProductName();
            merged.unit = row.getProductUnit();
            merged.spec = row.getProductSpec();
            merged.price = row.getProductPrice() == null ? BigDecimal.ZERO : row.getProductPrice();
            merged.num = row.getNum() == null ? BigDecimal.ZERO : row.getNum();
            merged.orderCode = row.getOrderCode();
            merged.customerDeptId = row.getCustomerDeptId();
            merged.sources.add(row);
            return merged;
        }

        Long getSkuId() { return skuId; }
        String getProductName() { return productName; }
        String getUnit() { return unit; }
        String getSpec() { return spec; }
        BigDecimal getPrice() { return price; }
        BigDecimal getNum() { return num; }
        void setNum(BigDecimal num) { this.num = num; }
        String getOrderCode() { return orderCode; }
        void setOrderCode(String orderCode) { this.orderCode = orderCode; }
        Long getCustomerDeptId() { return customerDeptId; }
        void setCustomerDeptId(Long customerDeptId) { this.customerDeptId = customerDeptId; }
        List<SaleOrderDetail> getSources() { return sources; }
    }
}

package com.lin.distribution.service.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DictUtils;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.AcceptanceStatus;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.DeliverySourceItem;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.dto.DeliveryNoPrintDTO;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.vo.DeliverySourceVO;
import com.lin.distribution.vo.DeliveryBatchPageVO;
import com.lin.distribution.vo.DeliveryMatrixLayout;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliverySourceItemMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.DeliveryOrderService;
import com.lin.distribution.util.PendingAcceptanceReminder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 送货单据Service业务层处理
 *
 * @author lin
 * @date 2024-12-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOrderServiceImpl implements DeliveryOrderService {

    /** 免纸送达原因字典（D-018） */
    private static final String DICT_NO_PRINT_REASON = "delivery_no_print_reason";
    /** 作废原因字典（T4/§5.2） */
    private static final String DICT_VOID_REASON = "delivery_void_reason";
    /** 字典「其他」编码：两套字典均要求必填补充说明 */
    private static final String REASON_OTHER = "other";

    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    private final DeliverySourceItemMapper deliverySourceItemMapper;
    private final CustomerDeptMapper customerDeptMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final AcceptanceMapper acceptanceMapper;

    /**
     * 查询送货单据
     *
     * @param id 送货单据主键
     * @return 送货单据
     */
    @Override
    public DeliveryOrder selectDeliveryOrderById(Long id) {
        return deliveryOrderMapper.selectDeliveryOrderById(id);
    }

    /**
     * 查询送货单明细列表（按商品合并行）
     *
     * @param deliveryId 送货单主键
     * @return 送货单明细集合
     */
    @Override
    public List<DeliveryOrderDetail> selectDetailListByDeliveryId(Long deliveryId) {
        return deliveryOrderDetailMapper.selectListByDeliveryId(deliveryId);
    }

    /**
     * 送货单来源视图（S14 §6.1/§八）：聚合行 + source_item 展开的来源订单/行/分配量。
     * 历史单（无台账）sources 为空列表，前端展示“—历史数据—”；
     * 订单号/配送点名批量回填，避免行级 N+1 查询。
     */
    @Override
    public List<DeliverySourceVO> selectDeliverySources(Long deliveryId) {
        List<DeliveryOrderDetail> details = deliveryOrderDetailMapper.selectListByDeliveryId(deliveryId);
        List<DeliverySourceItem> sources = deliverySourceItemMapper.selectListByDeliveryId(deliveryId);

        Map<Long, List<DeliverySourceItem>> byDetail = sources.stream()
                .collect(Collectors.groupingBy(DeliverySourceItem::getDeliveryDetailId));

        // 批量回填来源订单号与配送点名（无价格担忧：来源订单数/点数均为小量级）
        List<Long> orderIds = sources.stream()
                .map(DeliverySourceItem::getSaleOrderId).distinct().collect(Collectors.toList());
        Map<Long, String> orderCodeMap = orderIds.isEmpty() ? Map.of()
                : saleOrderMapper.selectSaleOrderByIdIn(orderIds).stream()
                        .collect(Collectors.toMap(SaleOrder::getId, SaleOrder::getCode, (a, b) -> a));
        List<Long> deptIds = sources.stream()
                .map(DeliverySourceItem::getCustomerDeptId).distinct().collect(Collectors.toList());
        Map<Long, String> deptNameMap = deptIds.isEmpty() ? Map.of()
                : deptIds.stream()
                        .map(customerDeptMapper::selectCustomerDeptById)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(CustomerDept::getId, CustomerDept::getName, (a, b) -> a));

        List<DeliverySourceVO> result = new ArrayList<>();
        for (DeliveryOrderDetail detail : details) {
            DeliverySourceVO vo = new DeliverySourceVO();
            vo.setDeliveryDetailId(detail.getId());
            vo.setProductName(detail.getProductName());
            vo.setProductSpec(detail.getProductSpec());
            vo.setProductUnit(detail.getProductUnit());
            vo.setNum(detail.getNum());
            vo.setPrice(detail.getPrice());
            vo.setAmount(detail.getAmount());
            vo.setSources(byDetail.getOrDefault(detail.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(DeliverySourceItem::getId))
                    .map(si -> {
                        DeliverySourceVO.SourceRow row = new DeliverySourceVO.SourceRow();
                        row.setSaleOrderId(si.getSaleOrderId());
                        row.setOrderCode(orderCodeMap.get(si.getSaleOrderId()));
                        row.setCustomerDeptId(si.getCustomerDeptId());
                        row.setCustomerDeptName(deptNameMap.get(si.getCustomerDeptId()));
                        row.setAllocatedQuantity(si.getAllocatedQuantity());
                        row.setUnitPrice(si.getUnitPrice());
                        return row;
                    })
                    .collect(Collectors.toList()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 查询送货单据列表（W0-3.2：已送达未提交验收的行实时计算待验收提醒级别，0无/1黄/2红，
     * 复用 {@link PendingAcceptanceReminder} 纯函数与工作台接口同口径）
     *
     * @param deliveryOrder 送货单据
     * @return 送货单据
     */
    @Override
    public List<DeliveryOrder> selectDeliveryOrderList(DeliveryOrder deliveryOrder) {
        List<DeliveryOrder> list = deliveryOrderMapper.selectDeliveryOrderList(deliveryOrder);
        fillReminderLevel(list);
        return list;
    }

    /**
     * 批次分组聚合（D-043）：按 客户+配送日期 分组。printForm 取批次布局快照（<无快照按 scopeType 推导>），
     * maxReminderLevel 按批次内已送达未验收单计算最高级（与单行标色同口径，聚合到批次主行）。
     */
    @Override
    public List<DeliveryBatchPageVO> selectBatchPage(DeliveryOrder deliveryOrder) {
        List<DeliveryBatchPageVO> batches = deliveryOrderMapper.selectBatchPage(deliveryOrder);
        if (CollectionUtils.isEmpty(batches)) {
            return batches;
        }
        // printForm：批次布局快照优先，无快照按 scopeType 推导
        for (DeliveryBatchPageVO batch : batches) {
            String scope = StringUtils.trimToNull(batch.getScopeType());
            if (StringUtils.isBlank(batch.getPrintForm())) {
                batch.setPrintForm(com.lin.distribution.constant.DeliveryScopeType.isCustomerDate(scope)
                        ? DeliveryMatrixLayout.FORM_MATRIX : DeliveryMatrixLayout.FORM_FLAT);
            }
        }
        // maxReminderLevel：批次内单行提醒最高级（已送达且无已提交验收，用 PendingAcceptanceReminder 同口径）
        fillBatchReminderLevel(batches);
        return batches;
    }

    /**
     * 批次主行待验收提醒最高级（D-043 聚合）。对批次内已送达未验收单计算提醒级，批次行取最高。
     *
     * <p>实现：查出这些「客户+日期」下的有效送货单，逐单算 reminder，再按批次取 max。</p>
     */
    private void fillBatchReminderLevel(List<DeliveryBatchPageVO> batches) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        for (DeliveryBatchPageVO batch : batches) {
            int max = 0;
            DeliveryOrder q = new DeliveryOrder();
            q.setCustomerId(batch.getCustomerId());
            q.setDeliveryDate(batch.getDeliveryDate() == null ? null : LocalDate.parse(batch.getDeliveryDate()));
            List<DeliveryOrder> orders = deliveryOrderMapper.selectDeliveryOrderList(q);
            // 只对已送达且无已提交验收的单计算
            Set<Long> submittedIds = submittedAcceptanceIds(orders);
            for (DeliveryOrder order : orders) {
                if (order.getStatus() == null || order.getStatus() != DeliveryOrderStatus.DELIVERED.getCode()
                        || submittedIds.contains(order.getId())) {
                    continue;
                }
                int level = PendingAcceptanceReminder.compute(order.getDeliveryDate(), order.getPrintTime(), today, now);
                max = Math.max(max, level);
            }
            batch.setMaxReminderLevel(Math.max(max, nvl(batch.getMaxReminderLevel())));
        }
    }

    /** 已提交验收的单号集合（同口径：已送达且已有验收单则不算待验收） */
    private Set<Long> submittedAcceptanceIds(List<DeliveryOrder> orders) {
        List<Long> deliveredIds = orders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == DeliveryOrderStatus.DELIVERED.getCode())
                .map(DeliveryOrder::getId)
                .distinct()
                .collect(Collectors.toList());
        if (deliveredIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(acceptanceMapper.selectSubmittedDeliveryIds(deliveredIds));
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * W0-3.2 列表提醒标色：仅 status=已送达且无已提交验收单的行参与分级
     * （与 WorkbenchController#pendingAcceptance 同口径：打印满2h→黄 / 配送日当天11:30后→红 / 过期→红）
     */
    private void fillReminderLevel(List<DeliveryOrder> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> pendingIds = list.stream()
                .filter(d -> d.getStatus() != null && d.getStatus() == DeliveryOrderStatus.DELIVERED.getCode())
                .map(DeliveryOrder::getId)
                .collect(Collectors.toList());
        if (pendingIds.isEmpty()) {
            return;
        }
        Set<Long> submittedIds = new HashSet<>(acceptanceMapper.selectSubmittedDeliveryIds(pendingIds));
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        for (DeliveryOrder order : list) {
            if (order.getStatus() == null || order.getStatus() != DeliveryOrderStatus.DELIVERED.getCode()
                    || submittedIds.contains(order.getId())) {
                continue;
            }
            int level = PendingAcceptanceReminder.compute(order.getDeliveryDate(), order.getPrintTime(), today, now);
            if (level != PendingAcceptanceReminder.LEVEL_NONE) {
                order.setReminderLevel(level);
                order.setReminderReason(PendingAcceptanceReminder.reason(level));
            }
        }
    }

    /**
     * 新增送货单据
     *
     * @param deliveryOrder 送货单据
     * @return 结果
     */
    @Override
    public int insertDeliveryOrder(DeliveryOrder deliveryOrder) {
        deliveryOrder.setCreateTime(DateUtils.getNowDate());
        return deliveryOrderMapper.insertDeliveryOrder(deliveryOrder);
    }

    /**
     * 修改送货单据
     *
     * @param deliveryOrder 送货单据
     * @return 结果
     */
    @Override
    public int updateDeliveryOrder(DeliveryOrder deliveryOrder) {
        deliveryOrder.setUpdateTime(DateUtils.getNowDate());
        return deliveryOrderMapper.updateDeliveryOrder(deliveryOrder);
    }

    /**
     * 批量删除送货单据
     *
     * @param ids 需要删除的送货单据主键
     * @return 结果
     */
    @Override
    public int deleteDeliveryOrderByIds(Long[] ids) {
        return deliveryOrderMapper.deleteDeliveryOrderByIds(ids);
    }

    /**
     * 删除送货单据信息
     *
     * @param id 送货单据主键
     * @return 结果
     */
    @Override
    public int deleteDeliveryOrderById(Long id) {
        return deliveryOrderMapper.deleteDeliveryOrderById(id);
    }

    /**
     * 标记打印：print_count + 1，状态 → 已打印
     */
    @Override
    @Transactional
    public DeliveryOrder markPrinted(Long id) {
        DeliveryOrder deliveryOrder = deliveryOrderMapper.selectDeliveryOrderById(id);
        if (deliveryOrder == null) {
            throw new ServiceException("送货单不存在");
        }
        if (Objects.equals(deliveryOrder.getStatus(), DeliveryOrderStatus.DELIVERED.getCode())) {
            throw new ServiceException("送货单已送达，不可再打印");
        }
        deliveryOrder.setPrintCount(deliveryOrder.getPrintCount() == null ? 1 : deliveryOrder.getPrintCount() + 1);
        deliveryOrder.setStatus(DeliveryOrderStatus.PRINTED.getCode());
        deliveryOrder.setPrintTime(DateUtils.getNowDate());
        deliveryOrder.setUpdateTime(DateUtils.getNowDate());
        deliveryOrderMapper.updateDeliveryOrder(deliveryOrder);
        return deliveryOrder;
    }

    /**
     * 标记送达（兼容旧调用）：未打印单缺少免纸原因将被拒绝
     */
    @Override
    @Transactional
    public DeliveryOrder markDelivered(Long id) {
        return markDelivered(id, null);
    }

    /**
     * 标记送达：状态 → 已送达，仅回写来源台账中的订单（S14/G2：IN 子查询，替代同组推断）。
     * 未打印（PENDING）直接送达必须登记免纸原因（D-018），拼接 remark 留痕。
     */
    @Override
    @Transactional
    public DeliveryOrder markDelivered(Long id, DeliveryNoPrintDTO noPrint) {
        DeliveryOrder deliveryOrder = deliveryOrderMapper.selectDeliveryOrderById(id);
        if (deliveryOrder == null) {
            throw new ServiceException("送货单不存在");
        }
        Integer status = deliveryOrder.getStatus();
        if (Objects.equals(status, DeliveryOrderStatus.DELIVERED.getCode())) {
            throw new ServiceException("送货单已送达，请勿重复操作");
        }
        if (DeliveryOrderStatus.VOIDED.getCode().equals(status)) {
            throw new ServiceException("送货单已作废，不可标记送达");
        }

        if (DeliveryOrderStatus.PENDING.getCode().equals(status)) {
            // 未打印送达：免纸原因为登记硬性要求（D-018：免纸/电子单据/录单补登/设备故障/其他）
            if (noPrint == null || StringUtils.isBlank(noPrint.getReasonCode())) {
                throw new ServiceException("未打印送货单直接送达，必须选择免纸送达原因");
            }
            if (REASON_OTHER.equals(noPrint.getReasonCode()) && StringUtils.isBlank(noPrint.getRemark())) {
                throw new ServiceException("免纸原因为其他时，必须填写补充说明");
            }
            String label = resolveDictLabel(DICT_NO_PRINT_REASON, noPrint.getReasonCode());
            String noPrintNote = StringUtils.isBlank(noPrint.getRemark())
                    ? label : label + "：" + noPrint.getRemark().trim();
            deliveryOrder.setRemark(StringUtils.isBlank(deliveryOrder.getRemark())
                    ? "[免纸送达:" + noPrintNote + "]"
                    : deliveryOrder.getRemark() + " [免纸送达:" + noPrintNote + "]");
        }

        deliveryOrder.setStatus(DeliveryOrderStatus.DELIVERED.getCode());
        deliveryOrder.setUpdateTime(DateUtils.getNowDate());
        deliveryOrderMapper.updateDeliveryOrder(deliveryOrder);

        // 仅回写本单来源分配命中的订单（DESIGN.md §4.2 回写矩阵）；同客户同日未进单订单不受影响
        saleOrderMapper.updateStatusByDeliveryId(
                id,
                SaleOrderStatus.CONFIRMED.getCode(),
                SaleOrderStatus.DELIVERED.getCode());
        return deliveryOrder;
    }

    /**
     * 作废送货单（S14/T4，DESIGN.md §5.2）：
     * 准入 PENDING/PRINTED；已有 SUBMITTED 验收或任一来源订单 SETTLED → 拒绝；
     * 动作：置 VOIDED + 原因/人/时间 + 来源分配软删释放订单（G4 解除锁定）。
     * 操作日志由 Controller @Log 写 sys_oper_log（含 reasonCode/reasonNote 请求参数）。
     */
    @Override
    @Transactional
    public void voidDeliveryOrder(Long id, String reasonCode, String reasonNote) {
        if (StringUtils.isBlank(reasonCode)) {
            throw new ServiceException("请选择作废原因");
        }
        if (REASON_OTHER.equals(reasonCode) && StringUtils.isBlank(reasonNote)) {
            throw new ServiceException("作废原因为其他时，必须填写补充说明");
        }
        DeliveryOrder deliveryOrder = deliveryOrderMapper.selectDeliveryOrderById(id);
        if (deliveryOrder == null) {
            throw new ServiceException("送货单不存在");
        }
        Integer status = deliveryOrder.getStatus();
        if (DeliveryOrderStatus.VOIDED.getCode().equals(status)) {
            throw new ServiceException("送货单已作废，请勿重复操作");
        }
        if (!DeliveryOrderStatus.PENDING.getCode().equals(status)
                && !DeliveryOrderStatus.PRINTED.getCode().equals(status)) {
            // DELIVERED 及其他状态不可作废：已送达单走验收撤销流程（§七 操作可行性矩阵）
            throw new ServiceException("已送达的送货单不可作废，如需调整请先撤销验收");
        }

        // 已有 SUBMITTED 验收 → 拒绝（验收单引用送货快照，作废会破坏一单一验）
        Acceptance query = new Acceptance();
        query.setDeliveryOrderId(id);
        query.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        if (CollectionUtils.isNotEmpty(acceptanceMapper.selectAcceptanceList(query))) {
            throw new ServiceException("该送货单已有已提交的验收单，请先撤销验收");
        }

        // 任一来源订单 SETTLED → 拒绝（结算依据链不可断）
        List<Long> sourceOrderIds = deliverySourceItemMapper.selectListByDeliveryId(id).stream()
                .map(DeliverySourceItem::getSaleOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(sourceOrderIds)) {
            List<SaleOrder> sourceOrders = saleOrderMapper.selectSaleOrderByIdIn(sourceOrderIds);
            String settledCodes = sourceOrders.stream()
                    .filter(o -> SaleOrderStatus.SETTLED.getCode().equals(o.getStatus()))
                    .map(SaleOrder::getCode)
                    .collect(Collectors.joining("、"));
            if (StringUtils.isNotBlank(settledCodes)) {
                throw new ServiceException("来源订单已结算，不可作废：" + settledCodes);
            }
        }

        String voidReason = StringUtils.isBlank(reasonNote)
                ? resolveDictLabel(DICT_VOID_REASON, reasonCode)
                : resolveDictLabel(DICT_VOID_REASON, reasonCode) + "：" + reasonNote.trim();

        deliveryOrder.setStatus(DeliveryOrderStatus.VOIDED.getCode());
        deliveryOrder.setVoidReason(voidReason);
        deliveryOrder.setVoidBy(resolveOperator());
        deliveryOrder.setVoidTime(DateUtils.getNowDate());
        deliveryOrder.setUpdateTime(DateUtils.getNowDate());
        deliveryOrderMapper.updateDeliveryOrder(deliveryOrder);

        // 来源分配软删释放订单（唯一键含 is_deleted，释放后重新生成不撞键；旧行保留审计）
        deliverySourceItemMapper.deleteByDeliveryId(id);
        log.info("[delivery void] 送货单 {} 已作废，原因：{}，释放来源订单 {} 个", deliveryOrder.getCode(), voidReason, sourceOrderIds.size());
    }

    /**
     * 字典标签解析：缓存/上下文不可用时回退原编码（void_reason 落库可读性优先，不强依赖字典缓存）
     */
    private String resolveDictLabel(String dictType, String dictValue) {
        try {
            String label = DictUtils.getDictLabel(dictType, dictValue);
            return StringUtils.isBlank(label) ? dictValue : label;
        } catch (Exception e) {
            return dictValue;
        }
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

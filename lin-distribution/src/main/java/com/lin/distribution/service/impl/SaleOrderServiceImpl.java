package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.SaleGeneratePreviewVO;
import com.lin.distribution.dto.SaleOrderCreateDTO;
import com.lin.distribution.dto.SaleOrderUpdateStatusDTO;
import com.lin.distribution.dto.WithdrawCascadeResultVO;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.DeliveryOrderService;
import com.lin.distribution.service.OrderWithdrawCascadeService;
import com.lin.distribution.service.SaleOrderService;
import com.lin.distribution.util.ShiftCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 销售订单Service业务层处理
 *
 * @author lin
 * @date 2024-11-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaleOrderServiceImpl implements SaleOrderService {
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderDetailMapper saleOrderDetailMapper;
    private final AcceptanceMapper acceptanceMapper;
    private final DeliveryOrderService deliveryOrderService;
    private final OrderWithdrawCascadeService orderWithdrawCascadeService;
    private final BizCodeService bizCodeService;
    private final CustomerMapper customerMapper;
    private final CustomerDeptMapper customerDeptMapper;

    /**
     * 查询销售订单
     *
     * @param id 销售订单主键
     * @return 销售订单
     */
    @Override
    public SaleOrder selectSaleOrderById(Long id) {
        return saleOrderMapper.selectSaleOrderById(id);
    }

    /**
     * 查询销售订单列表
     *
     * @param saleOrder 销售订单
     * @return 销售订单
     */
    @Override
    public List<SaleOrder> selectSaleOrderList(SaleOrder saleOrder) {
        return saleOrderMapper.selectSaleOrderList(saleOrder);
    }

    /**
     * 客户视角分组聚合分页（D-064）：一行 = 客户 + 配送日期。
     *
     * <p>纯直出：聚合在 SQL 层完成（分页单位 = 客户行），服务层不需要补字段——
     * 班次是订单级属性，只在子行展示（D-065）。</p>
     */
    @Override
    public List<com.lin.distribution.vo.SaleCustomerPageVO> selectCustomerPage(SaleOrder saleOrder) {
        return saleOrderMapper.selectCustomerPage(saleOrder);
    }

    /**
     * 新增销售订单
     *
     * @param saleOrder 销售订单
     * @return 结果
     */
    @Override
    public int insertSaleOrder(SaleOrder saleOrder) {
        saleOrder.setCreateTime(DateUtils.getNowDate());
        return saleOrderMapper.insertSaleOrder(saleOrder);
    }

    /**
     * 修改销售订单
     *
     * @param saleOrder 销售订单
     * @return 结果
     */
    @Override
    public int updateSaleOrder(SaleOrder saleOrder) {
        saleOrder.setUpdateTime(DateUtils.getNowDate());
        return saleOrderMapper.updateSaleOrder(saleOrder);
    }

    /**
     * 批量删除销售订单（2026-09-14 改为**逻辑删除**）：
     * <ul>
     *   <li>护栏：仅「草稿」可删——已确认及之后的单已进入采购/送货/验收链路，删除会断链；</li>
     *   <li>级联：明细同步逻辑删除，避免残留孤儿明细行；</li>
     *   <li>单号不释放：{@code selectSaleOrderByCode} 故意不过滤 is_deleted，逻辑删除行的单号仍被占用。</li>
     * </ul>
     *
     * @param ids 需要删除的销售订单主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteSaleOrderByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (Long id : ids) {
            SaleOrder order = saleOrderMapper.selectSaleOrderById(id);
            if (order == null) {
                continue;
            }
            if (!SaleOrderStatus.DRAFT.getCode().equals(order.getStatus())) {
                throw new ServiceException("仅草稿状态可删除；已确认及之后的订单请先「撤回」或以作废/退货方式处理："
                        + order.getCode());
            }
        }
        saleOrderDetailMapper.deleteSaleOrderDetailByOrderIds(ids);
        return saleOrderMapper.deleteSaleOrderByIds(ids);
    }

    /**
     * 删除销售订单信息（同批量口径：仅草稿 + 主单/明细逻辑删除）
     *
     * @param id 销售订单主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteSaleOrderById(Long id) {
        return deleteSaleOrderByIds(new Long[]{id});
    }


    @Override
    public String generateSaleOrderNo(Boolean refresh, String currentCode) {
        return generateOrderNo(refresh, currentCode);
    }

    @Override
    @Transactional
    public SaleOrder createSaleOrder(SaleOrderCreateDTO request) {
        checkCreateOrUpdateOrderRequest(request);

        Long customerId = request.getCustomerId();
        Long customerDeptId = request.getCustomerDeptId();
        String orderCode = request.getOrderCode();
        List<SaleOrderDetail> orderDetails = request.getOrderDetails();
        // calc amount (校验订单金额不得为 0)
        BigDecimal amount = calcAndValidateOrderAmount(orderDetails);
        // 班次（s35）：未启用班次的客户归一化为空串，启用时校验落在该配送点声明范围内
        String shiftCode = resolveShiftCode(customerId, customerDeptId, request.getShiftCode());

        // insert order
        SaleOrder order = SaleOrder.builder()
                .customerId(customerId)
                .customerDeptId(customerDeptId)
                .shiftCode(shiftCode)
                .code(orderCode)
                .deliveryDate(request.getDeliveryDate())
                .amount(amount)
                .status(SaleOrderStatus.DRAFT.getCode())
                .source(1)
                .type(1)
                .version(0)
                .isDeleted(Boolean.FALSE)
                .build();
        order.setRemark(request.getRemark());
        // 维护 update_time（草稿箱陈旧判定基线：同编号订单被保存后 update_time 应变化）
        order.setUpdateTime(DateUtils.getNowDate());
        saleOrderMapper.insertSaleOrder(order);

        // batch insert order details
        Long orderId = order.getId();
        orderDetails.forEach(detail -> {
            detail.setOrderId(orderId);
            detail.setOrderCode(orderCode);
            detail.setCustomerId(customerId);
            detail.setCustomerDeptId(customerDeptId);
            detail.setIsDeleted(Boolean.FALSE);
            detail.setVersion(0);
            saleOrderDetailMapper.insertSaleOrderDetail(detail);
        });

        // increase orderCode (pass currentCode to prevent double-allocation)
        generateOrderNo(true, orderCode);

        return order;
    }

    @Override
    @Transactional
    public SaleOrder updateSaleOrderWithDetails(SaleOrderCreateDTO request) {
        checkCreateOrUpdateOrderRequest(request);
        Long orderId = request.getOrderId();
        if (orderId == null) {
            throw new ServiceException("order id is null");
        }

        Long customerId = request.getCustomerId();
        Long customerDeptId = request.getCustomerDeptId();
        String orderCode = request.getOrderCode();
        List<SaleOrderDetail> orderDetails = request.getOrderDetails();
        // calc amount (校验订单金额不得为 0)
        BigDecimal amount = calcAndValidateOrderAmount(orderDetails);

        SaleOrder order = saleOrderMapper.selectSaleOrderById(orderId);
        if (order == null) {
            throw new ServiceException("销售订单不存在");
        }
        // 编辑护栏（S14/G6，DESIGN.md §5.5）：状态机+是否已分配送货单双重拦截
        checkOrderEditable(order);
        // 快照不可变护栏（蓝图 W0-1/「基础资料快照」）：客户/配送点/单号为订单快照标识字段，
        // 编辑时不可变更；换客户或换配送点应新开订单，主数据修改不回写历史/未确认订单
        checkSnapshotImmutable(order, request);
        // 班次（s35）：与配送点同为订单快照标识，编辑时不可变更；
        // 历史无班次单按白班归一化后比对，并在本次落库时补齐为白班（业务口径：无班次归白班）
        String shiftCode = resolveShiftCode(customerId, customerDeptId, request.getShiftCode());
        // s35：快照不可变仅约束「已落班次」的订单；历史无班次单（空）允许本次落库补齐。
        // 否则配送点声明里没有 DAY（如只配夜班）时，历史单会被「班次不可修改」死锁，无法编辑
        if (StringUtils.isNotBlank(order.getShiftCode()) && !shiftCode.equals(order.getShiftCode().trim())) {
            throw new ServiceException("订单班次不可修改，如需更换班次请新开订单：" + order.getCode());
        }

        // update order
        order.setDeliveryDate(request.getDeliveryDate());
        order.setAmount(amount);
        order.setShiftCode(shiftCode);
        order.setRemark(request.getRemark());
        order.setUpdateTime(DateUtils.getNowDate());
        saleOrderMapper.updateSaleOrder(order);

        saleOrderDetailMapper.deleteSaleOrderDetailByOrderId(orderId);

        // batch insert order details
        orderDetails.forEach(detail -> {
            detail.setOrderId(orderId);
            detail.setOrderCode(orderCode);
            detail.setCustomerId(customerId);
            detail.setCustomerDeptId(customerDeptId);
            detail.setIsDeleted(false);
            detail.setVersion(0);
            saleOrderDetailMapper.insertSaleOrderDetail(detail);
        });

        return order;
    }

    @Override
    public List<SaleOrder> selectRecentOrderList(Long customerId, String keyword, Integer recentDays, List<Integer> statuses) {
        int days = Optional.ofNullable(recentDays).orElse(7);
        LocalDateTime createEndTime = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime createStartTime = LocalDate.now().minusDays(days).atStartOfDay();
        // 默认只看「草稿 + 已确认」：这两类才是录单时需要参考/继续处理的单；
        // 显式传空集合=全部状态（前端「全部」筛选），传非空=按传入状态过滤
        List<Integer> effectiveStatuses = statuses == null
                ? List.of(SaleOrderStatus.DRAFT.getCode(), SaleOrderStatus.CONFIRMED.getCode())
                : statuses;
        return saleOrderMapper.selectRecentOrderList(customerId, keyword, createStartTime, createEndTime, effectiveStatuses);
    }

    @Override
    public List<com.lin.distribution.vo.SaleOrderBriefVO> selectBriefByCodes(List<String> codes) {
        if (CollectionUtils.isEmpty(codes)) {
            return List.of();
        }
        // 去重 + 去空，避免 IN 里带重复/空串
        List<String> normalized = codes.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return saleOrderMapper.selectSaleOrderByCodes(normalized).stream()
                .map(order -> new com.lin.distribution.vo.SaleOrderBriefVO(
                        order.getId(),
                        order.getCode(),
                        order.getStatus(),
                        order.getUpdateTime() == null ? null : DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, order.getUpdateTime())))
                .toList();
    }

    @Override
    @Transactional
    public void updateSaleOrderStatus(SaleOrderUpdateStatusDTO request) {
        List<Long> orderIds = request.getOrderIds();
        if (CollectionUtils.isEmpty(orderIds)) {
            throw new ServiceException("order ids is empty");
        }
        SaleOrderStatus newStatus = SaleOrderStatus.fromCode(request.getStatus());
        List<SaleOrder> orders = saleOrderMapper.selectSaleOrderByIdIn(orderIds);
        if (CollectionUtils.isEmpty(orders)) {
            throw new ServiceException("no sale orders found for the given ids");
        }

        // check new order status
        boolean checkNewStatus = false;
        switch (newStatus) {
            case DRAFT, DELIVERED ->
                    checkNewStatus = orders.stream().allMatch(it -> SaleOrderStatus.CONFIRMED.getCode().equals(it.getStatus()));
            case CONFIRMED ->
                    checkNewStatus = orders.stream().allMatch(it -> SaleOrderStatus.DRAFT.getCode().equals(it.getStatus()));
            case ACCEPTED ->
                    checkNewStatus = orders.stream().allMatch(it -> SaleOrderStatus.DELIVERED.getCode().equals(it.getStatus()));
            case SETTLED ->
                    checkNewStatus = orders.stream().allMatch(it -> SaleOrderStatus.ACCEPTED.getCode().equals(it.getStatus()));
        }
        if (!checkNewStatus) {
            throw new ServiceException("check new order status error");
        }

        // 撤回（DRAFT，W0-2.1 级联）：先对全部待撤回订单无副作用预检（被已打印/已送达送货单
        // 或已入库采购单占用 → 拒绝），再逐单级联扣除/作废未打印送货单与未入库采购单
        // （蓝图「撤回级联/共享单据撤回/空关联单据」）；任一失败整体回滚
        if (newStatus == SaleOrderStatus.DRAFT) {
            for (SaleOrder order : orders) {
                orderWithdrawCascadeService.validateOrderWithdrawable(order);
            }
            for (SaleOrder order : orders) {
                WithdrawCascadeResultVO cascade = orderWithdrawCascadeService.cascadeOnOrderWithdraw(order);
                log.info("[sale order withdraw] 订单 {} 撤回级联：作废送货单 {}，扣除送货单 {}，作废采购单 {}，扣除采购单 {}",
                        order.getCode(), cascade.getVoidedDeliveryCodes(), cascade.getDeductedDeliveryCodes(),
                        cascade.getVoidedPurchaseCodes(), cascade.getDeductedPurchaseCodes());
            }
        }

        // update order list
        for (SaleOrder order : orders) {
            order.setStatus(newStatus.getCode());
            order.setUpdateTime(DateUtils.getNowDate());
            saleOrderMapper.updateSaleOrder(order);
        }

        // S1-1.3 手工定价审计：订单确认时汇总手工定价行写操作日志（来源/原价/原因随订单行持久化）
        if (newStatus == SaleOrderStatus.CONFIRMED) {
            for (SaleOrder order : orders) {
                logManualPricingOnConfirm(order);
            }
        }
    }

    /**
     * 订单确认时输出手工定价审计日志（S1-1.3，蓝图「手工定价：审计可追溯」）。
     * 审计落点：行级 price_source/ref_price/price_reason 持久化 + 此处操作日志（sys_oper_log 经 @Log 另有状态变更记录）。
     */
    private void logManualPricingOnConfirm(SaleOrder order) {
        SaleOrderDetail query = new SaleOrderDetail();
        query.setOrderId(order.getId());
        List<SaleOrderDetail> details = saleOrderDetailMapper.selectSaleOrderDetailList(query);
        List<String> manualLines = new ArrayList<>();
        for (SaleOrderDetail detail : details) {
            if (detail.getIsDeleted() == null || detail.getIsDeleted()) {
                continue;
            }
            if ("manual".equals(detail.getPriceSource())) {
                manualLines.add(detail.getProductName()
                        + "：单价 " + detail.getProductPrice()
                        + (detail.getRefPrice() != null ? "（原建议价 " + detail.getRefPrice() + "）" : "（无报价）"));
            }
        }
        if (!manualLines.isEmpty()) {
            log.info("[sale order confirm] 订单 {} 含 {} 行手工定价（操作者：{}）：{}",
                    order.getCode(), manualLines.size(), resolveOperator(), String.join("；", manualLines));
        }
    }

    /** 安全获取当前登录操作者（定时/系统调用场景回退 system） */
    private String resolveOperator() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }

    /**
     * 计算订单总金额，并校验订单金额不得为 0（不允许提交 0 金额订单）
     */
    private BigDecimal calcAndValidateOrderAmount(List<SaleOrderDetail> orderDetails) {
        BigDecimal amount = orderDetails.stream().map(it -> {
            BigDecimal productNum = Optional.ofNullable(it.getNum()).orElse(BigDecimal.ZERO);
            BigDecimal productPrice = Optional.ofNullable(it.getProductPrice()).orElse(BigDecimal.ZERO);
            BigDecimal expectAmount = NumberUtils.toScaledBigDecimal(productNum.multiply(productPrice), 2, RoundingMode.HALF_UP);
            // set expectAmount
            it.setExpectAmount(expectAmount);
            // return to calc sum amount
            return expectAmount;
        }).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("订单金额为 0，不允许提交订单");
        }
        return amount;
    }

    private void checkCreateOrUpdateOrderRequest(SaleOrderCreateDTO request) {
        List<SaleOrderDetail> orderDetails = request.getOrderDetails();
        if (CollectionUtils.isEmpty(orderDetails)) {
            throw new ServiceException("order details is empty");
        }

        Long orderId = request.getOrderId();
        if (orderId == null) {
            // check order code
            SaleOrder saleOrder = saleOrderMapper.selectSaleOrderByCode(request.getOrderCode());
            if (saleOrder != null) {
                throw new ServiceException("销售订单编号已存在");
            }
        }
        // 更新分支的状态护栏由 checkOrderEditable 统一处理（S14/G6：DRAFT/未分配 CONFIRMED 可改，
        // 其余按状态与分配情况拒绝）
    }

    /**
     * 解析并校验订单班次（s35《配送点班次配置》）：
     * <ul>
     *   <li>客户未启用班次 → 一律返回空串（不落库班次，行为与引入前一致）；</li>
     *   <li>客户启用班次 → 必落在该配送点声明的班次内；单班次配送点可省略（自动带出）；</li>
     *   <li>配送点未声明任何班次 → 拒绝（提示先维护，避免静默丢班次维度）。</li>
     * </ul>
     */
    private String resolveShiftCode(Long customerId, Long customerDeptId, String requested) {
        Customer customer = customerId == null ? null : customerMapper.selectCustomerById(customerId);
        if (customer == null || !BooleanUtils.isTrue(customer.getShiftEnabled())) {
            return "";
        }
        CustomerDept dept = customerDeptId == null ? null : customerDeptMapper.selectCustomerDeptById(customerDeptId);
        if (dept == null) {
            throw new ServiceException("配送点不存在：" + customerDeptId);
        }
        List<String> supported = ShiftCodes.parse(dept.getShiftCodes());
        if (supported.isEmpty()) {
            throw new ServiceException("该客户已启用班次，但配送点【" + dept.getName() + "】未配置班次，请先在配送点维护班次");
        }
        String shift = StringUtils.trimToEmpty(requested);
        if (shift.isEmpty()) {
            if (supported.size() == 1) {
                return supported.get(0);
            }
            throw new ServiceException("请选择班次（配送点【" + dept.getName() + "】支持：" + String.join("/", supported) + "）");
        }
        if (!supported.contains(shift)) {
            throw new ServiceException("班次不在配送点【" + dept.getName() + "】支持的范围内："
                    + shift + "（支持：" + String.join("/", supported) + "）");
        }
        return shift;
    }

    /**
     * 订单编辑护栏（S14/G6 + D-055/OA + 2026-09-14 状态收敛，DESIGN.md §5.5 / §七 操作可行性矩阵）：
     * <p><b>只有「草稿」可直接改单</b>（2026-09-14 业务定稿）：已确认及之后必须先在订单列表
     * 「撤回」为草稿，避免“已确认单被静默改内容、已生成的下游单据对不上”。</p>
     * DELIVERED/ACCEPTED → 拒改（配送后的调整用配送后变更标记/新增销售订单）；
     * SETTLED → 拒改；
     * CONFIRMED + 已进有效送货单 → 拒改（提示先作废送货单）；
     * CONFIRMED（其他）→ 拒改（提示先撤回）；
     * 草稿 + 已存在验收单（草稿/已提交）→ 拒改（整单重写会把验收行 dangling 并重复补行）；
     * 草稿 + 存在配送后变更标记（加单/换货/退货）→ 拒改（整单重写会静默丢弃标记）。
     */
    private void checkOrderEditable(SaleOrder order) {
        Integer status = order.getStatus();
        if (!SaleOrderStatus.DRAFT.getCode().equals(status)) {
            if (SaleOrderStatus.DELIVERED.getCode().equals(status)
                    || SaleOrderStatus.ACCEPTED.getCode().equals(status)) {
                throw new ServiceException("配送后的订单不可修改，如需调整请使用配送后变更（加单/退货标记）或新增销售订单：" + order.getCode());
            }
            if (SaleOrderStatus.SETTLED.getCode().equals(status)) {
                throw new ServiceException("已结算订单禁止修改：" + order.getCode());
            }
            if (SaleOrderStatus.CONFIRMED.getCode().equals(status)
                    && saleOrderDetailMapper.existsValidAllocation(order.getId())) {
                throw new ServiceException("该订单已进入 D-055 前的历史送货单，请先作废对应送货单后再修改：" + order.getCode());
            }
            throw new ServiceException("已确认订单不可直接修改，请先在订单列表「撤回」为草稿后再修改：" + order.getCode());
        }
        // D-055/OA（P0）：整单重写会物理删除并重插明细（id 全变），
        // 草稿仍可能带历史痕迹（曾确认后被撤回）→ 同样需排除验收单与配送后变更标记
        Acceptance acceptance = acceptanceMapper.selectBySaleOrder(order.getId());
        if (acceptance != null) {
            throw new ServiceException("该订单已生成验收单【" + acceptance.getCode()
                    + "】，请先撤销或删除验收单后再修改：" + order.getCode());
        }
        if (saleOrderDetailMapper.existsChangeMark(order.getId())) {
            throw new ServiceException("该订单存在配送后变更（加单/换货/退货）标记，"
                    + "请在订单明细页回退变更后再修改，或直接使用变更入口：" + order.getCode());
        }
    }

    /**
     * 订单快照不可变护栏（蓝图「基础资料快照」，W0-1）。
     * 客户、配送点、单号为录单时固化的快照标识字段：编辑订单时必须与原单一致，
     * 任何变更（换客户/换配送点）都应新开订单，保证订单头与明细行快照的一致性。
     */
    private void checkSnapshotImmutable(SaleOrder order, SaleOrderCreateDTO request) {
        if (!Objects.equals(order.getCustomerId(), request.getCustomerId())) {
            throw new ServiceException("订单客户不可修改，如需为其他客户下单请新开订单：" + order.getCode());
        }
        if (!Objects.equals(order.getCustomerDeptId(), request.getCustomerDeptId())) {
            throw new ServiceException("订单配送点不可修改，更换配送点请新开订单：" + order.getCode());
        }
        if (!StringUtils.equals(order.getCode(), request.getOrderCode())) {
            throw new ServiceException("订单编号不可修改：" + order.getCode());
        }
    }

    private String generateOrderNo(Boolean refresh) {
        return generateOrderNo(refresh, null);
    }

    private String generateOrderNo(Boolean refresh, String currentCode) {
        // XDyyyyMMdd + 每日重置序号（DB 序列，Redis 非硬依赖）
        String peekCode = bizCodeService.peekDailyCode("saleOrder", "XD", 4);
        if (StringUtils.equals(peekCode, currentCode)) {
            return peekCode;
        }
        if (!BooleanUtils.isTrue(refresh)) {
            return peekCode;
        }
        // 刷新：取下一个号；若已被 t_sale_order 占用（含逻辑删除行），继续跳号直到可用
        String code = bizCodeService.nextDailyCode("saleOrder", "XD", 4);
        int guard = 0;
        while (saleOrderMapper.selectSaleOrderByCode(code) != null && guard++ < 100) {
            code = bizCodeService.nextDailyCode("saleOrder", "XD", 4);
        }
        return code;
    }

    /**
     * 查询同配送点+同日期（启用班次时再加同班次）的草稿订单（新增订单时，选中客户后检测是否已有可继续添加的草稿）。
     *
     * <p>s35：启用班次的客户，不同班次是两次独立配送，不算重复（否则白班草稿会拦住夜班录单）；
     * 未启用班次的客户忽略班次，判重口径与引入前完全一致。</p>
     */
    @Override
    public SaleOrder findExistingDraftOrder(Long customerDeptId, LocalDate deliveryDate, String shiftCode) {
        if (customerDeptId == null || deliveryDate == null) {
            return null;
        }
        // 班次过滤值：仅启用班次的客户参与；空班次按白班归一化（与矩阵/订单落库同一口径）
        String shiftFilter = null;
        CustomerDept dept = customerDeptMapper.selectCustomerDeptById(customerDeptId);
        Customer customer = dept == null ? null : customerMapper.selectCustomerById(dept.getCustomerId());
        if (customer != null && BooleanUtils.isTrue(customer.getShiftEnabled())) {
            shiftFilter = ShiftCodes.orDefault(shiftCode);
        }
        List<SaleOrder> list = saleOrderMapper.selectExistingDraftOrder(customerDeptId, deliveryDate, shiftFilter,
                ShiftCodes.DAY);
        return CollectionUtils.isNotEmpty(list) ? list.get(0) : null;
    }

    /**
     * 生成单据前汇总预览（Phase 2，销售订单列表页抽屉第一步）
     * 校验订单均为已确认，按品类分组聚合明细（临时商品归"临时商品"）。
     */
    @Override
    public SaleGeneratePreviewVO generatePreview(List<Long> orderIds) {
        if (CollectionUtils.isEmpty(orderIds)) {
            throw new ServiceException("请选择要预览的订单");
        }
        List<Long> distinctIds = orderIds.stream().distinct().collect(Collectors.toList());

        List<SaleOrder> orders = saleOrderMapper.selectSaleOrderByIdIn(distinctIds);
        if (orders.size() != distinctIds.size()) {
            throw new ServiceException("部分订单不存在或已删除，请刷新列表后重试");
        }
        String invalidCodes = orders.stream()
                .filter(o -> !SaleOrderStatus.CONFIRMED.getCode().equals(o.getStatus()))
                .map(SaleOrder::getCode)
                .collect(Collectors.joining(","));
        if (StringUtils.isNotEmpty(invalidCodes)) {
            throw new ServiceException("以下订单不是审核状态：" + invalidCodes);
        }

        List<SaleOrderDetail> aggregated = saleOrderDetailMapper.selectPreviewByOrderIds(distinctIds);
        Map<String, List<SaleOrderDetail>> groupMap = aggregated.stream().collect(Collectors.groupingBy(
                it -> StringUtils.defaultIfBlank(it.getCategoryName(), "临时商品"),
                LinkedHashMap::new, Collectors.toList()));

        List<SaleGeneratePreviewVO.PreviewGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<SaleOrderDetail>> entry : groupMap.entrySet()) {
            BigDecimal quantity = BigDecimal.ZERO;
            BigDecimal amount = BigDecimal.ZERO;
            List<SaleGeneratePreviewVO.PreviewItem> items = new ArrayList<>();
            for (SaleOrderDetail detail : entry.getValue()) {
                BigDecimal qty = Optional.ofNullable(detail.getNum()).orElse(BigDecimal.ZERO);
                BigDecimal price = Optional.ofNullable(detail.getProductPrice()).orElse(BigDecimal.ZERO);
                BigDecimal amt = Optional.ofNullable(detail.getExpectAmount()).orElse(qty.multiply(price));
                quantity = quantity.add(qty);
                amount = amount.add(amt);
                items.add(SaleGeneratePreviewVO.PreviewItem.builder()
                        .skuId(detail.getSkuId())
                        .productName(detail.getProductName())
                        .productSpec(detail.getProductSpec())
                        .productUnit(detail.getProductUnit())
                        .quantity(qty)
                        .price(price)
                        .amount(amt)
                        .build());
            }
            groups.add(SaleGeneratePreviewVO.PreviewGroup.builder()
                    .categoryName(entry.getKey())
                    .items(items)
                    .quantity(quantity)
                    .amount(amount)
                    .build());
        }

        BigDecimal totalQuantity = groups.stream()
                .map(SaleGeneratePreviewVO.PreviewGroup::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = orders.stream()
                .map(SaleOrder::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SaleGeneratePreviewVO.PreviewOrder> previewOrders = orders.stream()
                .map(o -> SaleGeneratePreviewVO.PreviewOrder.builder()
                        .id(o.getId())
                        .code(o.getCode())
                        .customerName(o.getCustomerName())
                        .customerDeptName(o.getCustomerDeptName())
                        .deliveryName(o.getDeliveryName())
                        .deliveryDate(o.getDeliveryDate())
                        .amount(o.getAmount())
                        .build())
                .collect(Collectors.toList());

        return SaleGeneratePreviewVO.builder()
                .orders(previewOrders)
                .groups(groups)
                .itemCount(aggregated.size())
                .totalQuantity(totalQuantity)
                .totalAmount(totalAmount)
                .build();
    }
}

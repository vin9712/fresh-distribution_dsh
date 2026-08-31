package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.constant.MonthAdjustmentStatus;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.MonthAdjustment;
import com.lin.distribution.domain.MonthSettlement;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.MonthAdjustmentMapper;
import com.lin.distribution.mapper.MonthSettlementMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.MonthAdjustmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 下月调整单Service实现（蓝图 W0-2.7）
 *
 * <p>独立单号、草稿/已提交；应收金额与采购成本分项独立留痕（可正可负），用于月结后纠错，
 * 不改写原订单快照。提交后立即参与客户对账与经营概览重算（W0-3 挂接）。</p>
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthAdjustmentServiceImpl implements MonthAdjustmentService {

    private final MonthAdjustmentMapper monthAdjustmentMapper;
    private final CustomerMapper customerMapper;
    private final MonthSettlementMapper monthSettlementMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final AcceptanceMapper acceptanceMapper;
    private final BizCodeService bizCodeService;

    @Override
    public List<MonthAdjustment> selectList(MonthAdjustment query) {
        return monthAdjustmentMapper.selectList(query);
    }

    @Override
    public MonthAdjustment selectById(Long id) {
        return monthAdjustmentMapper.selectById(id);
    }

    @Override
    @Transactional
    public MonthAdjustment create(MonthAdjustment adjustment) {
        if (adjustment == null || adjustment.getCustomerId() == null) {
            throw new ServiceException("请选择调整客户");
        }
        Customer customer = customerMapper.selectCustomerById(adjustment.getCustomerId());
        if (customer == null) {
            throw new ServiceException("客户不存在");
        }
        if (StringUtils.isBlank(adjustment.getBillMonth())) {
            throw new ServiceException("请填写结算所属月份（yyyy-MM）");
        }
        BigDecimal receivable = nvl(adjustment.getReceivableAmount());
        BigDecimal cost = nvl(adjustment.getPurchaseCostAmount());
        if (receivable.compareTo(BigDecimal.ZERO) == 0 && cost.compareTo(BigDecimal.ZERO) == 0) {
            throw new ServiceException("应收调整与采购成本调整不能同时为 0");
        }

        adjustment.setCode(bizCodeService.nextDailyCode("monthAdjustment", "TJ", 3));
        adjustment.setReceivableAmount(receivable);
        adjustment.setPurchaseCostAmount(cost);
        adjustment.setStatus(MonthAdjustmentStatus.DRAFT.getCode());
        adjustment.setIsDeleted(0);
        adjustment.setCreateTime(DateUtils.getNowDate());
        monthAdjustmentMapper.insert(adjustment);
        return adjustment;
    }

    @Override
    @Transactional
    public int update(MonthAdjustment adjustment) {
        if (adjustment == null || adjustment.getId() == null) {
            throw new ServiceException("调整单ID不能为空");
        }
        String billMonth = adjustment.getBillMonth();
        MonthAdjustment exist = getExist(adjustment.getId());
        if (!MonthAdjustmentStatus.DRAFT.getCode().equals(exist.getStatus())) {
            throw new ServiceException("仅草稿状态可修改");
        }
        if (StringUtils.isBlank(billMonth)) {
            throw new ServiceException("请填写结算所属月份（yyyy-MM）");
        }
        // 月结冻结校验（W0-3.1）：已月结客户该月调整单不可改
        checkNotSettled(exist.getCustomerId(), billMonth);
        monthAdjustmentMapper.update(adjustment);
        return 1;
    }

    @Override
    @Transactional
    public int submit(Long id) {
        MonthAdjustment exist = getExist(id);
        if (!MonthAdjustmentStatus.DRAFT.getCode().equals(exist.getStatus())) {
            throw new ServiceException("仅草稿状态可提交");
        }
        MonthAdjustment update = new MonthAdjustment();
        update.setId(id);
        update.setStatus(MonthAdjustmentStatus.SUBMITTED.getCode());
        monthAdjustmentMapper.update(update);
        log.info("[month adjustment] 下月调整单 {} 已提交（客户 {}，{}）", exist.getCode(), exist.getCustomerId(), exist.getBillMonth());
        return 1;
    }

    @Override
    @Transactional
    public int delete(Long id) {
        MonthAdjustment exist = getExist(id);
        if (!MonthAdjustmentStatus.DRAFT.getCode().equals(exist.getStatus())) {
            throw new ServiceException("仅草稿状态可删除");
        }
        // 月结冻结校验（W0-3.1）：已月结客户该月调整单不可删
        checkNotSettled(exist.getCustomerId(), exist.getBillMonth());
        return monthAdjustmentMapper.deleteById(id);
    }

    /**
     * 原订单关联摘要（蓝图 §2「月结调整追溯」）：t_month_adjustment 仅有「客户+结算月」粒度
     * （无订单级外键），订单级摘要=该客户订单归月（最近已提交验收单 accept_date 所在月）
     * 下的调整单列表，不改写原订单快照；未验收归月的订单返回空摘要。
     */
    @Override
    public com.lin.distribution.vo.OrderAdjustmentSummaryVO selectBySaleOrderId(Long saleOrderId) {
        com.lin.distribution.vo.OrderAdjustmentSummaryVO vo = new com.lin.distribution.vo.OrderAdjustmentSummaryVO();
        vo.setAdjustments(List.of());
        vo.setReceivableTotal(BigDecimal.ZERO);
        vo.setCostTotal(BigDecimal.ZERO);
        SaleOrder order = saleOrderMapper.selectSaleOrderById(saleOrderId);
        if (order == null || order.getCustomerId() == null) {
            return vo;
        }
        java.time.LocalDate latestAcceptDate = acceptanceMapper.selectLatestAcceptDateBySaleOrderId(saleOrderId);
        if (latestAcceptDate == null) {
            return vo; // 尚未验收归月，无关联调整
        }
        String billMonth = latestAcceptDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        MonthAdjustment query = new MonthAdjustment();
        query.setCustomerId(order.getCustomerId());
        query.setBillMonth(billMonth);
        List<MonthAdjustment> adjustments = monthAdjustmentMapper.selectList(query);
        vo.setBillMonth(billMonth);
        vo.setAdjustments(adjustments);
        vo.setReceivableTotal(adjustments.stream()
                .map(a -> a.getReceivableAmount() == null ? BigDecimal.ZERO : a.getReceivableAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setCostTotal(adjustments.stream()
                .map(a -> a.getPurchaseCostAmount() == null ? BigDecimal.ZERO : a.getPurchaseCostAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return vo;
    }

    /**
     * 月结冻结校验（W0-3.1）：已月结客户该月调整单不可改/删（纠错只能新建下月调整单）
     */
    private void checkNotSettled(Long customerId, String billMonth) {
        if (customerId == null || billMonth == null) {
            return;
        }
        MonthSettlement s = monthSettlementMapper.selectByCustomerAndMonth(customerId, billMonth);
        if (s != null && Integer.valueOf(1).equals(s.getStatus())) {
            throw new ServiceException("客户该月（" + billMonth + "）已月结，调整单已冻结");
        }
    }

    private MonthAdjustment getExist(Long id) {
        if (id == null) {
            throw new ServiceException("调整单ID不能为空");
        }
        MonthAdjustment exist = monthAdjustmentMapper.selectById(id);
        if (exist == null || Integer.valueOf(1).equals(exist.getIsDeleted())) {
            throw new ServiceException("调整单不存在");
        }
        return exist;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

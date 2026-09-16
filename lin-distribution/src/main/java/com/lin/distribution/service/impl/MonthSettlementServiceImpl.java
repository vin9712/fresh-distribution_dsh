package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.AcceptanceStatus;
import com.lin.distribution.constant.MonthAdjustmentStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.MonthAdjustment;
import com.lin.distribution.domain.MonthSettlement;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.MonthAdjustmentMapper;
import com.lin.distribution.mapper.MonthSettlementMapper;
import com.lin.distribution.service.AcceptanceService;
import com.lin.distribution.service.MonthSettlementService;
import com.lin.distribution.vo.MonthSettlementPreviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 客户月度结算Service实现（蓝图 W0-3.1 按客户月结）
 *
 * <p>口径：按「客户 + 结算月」锁定；验收单按 accept_date 归月、下月调整单按 bill_month 归月、
 * 采购成本按采购单 order_date 归月（供应商池化无 customer_id，按月粒度冻结，见冻结校验备注）。
 * 月结后该客户该月验收单/采购成本/调整单均冻结，纠错走《下月调整单》（W0-2.7）。</p>
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthSettlementServiceImpl implements MonthSettlementService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MonthSettlementMapper monthSettlementMapper;
    private final MonthAdjustmentMapper monthAdjustmentMapper;
    private final CustomerMapper customerMapper;
    private final AcceptanceService acceptanceService;

    @Override
    public MonthSettlementPreviewVO preview(Long customerId, String billMonth) {
        validate(customerId, billMonth);
        YearMonth ym = YearMonth.parse(billMonth);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        MonthSettlementPreviewVO vo = new MonthSettlementPreviewVO();
        vo.setCustomerId(customerId);
        vo.setBillMonth(billMonth);
        Customer customer = customerMapper.selectCustomerById(customerId);
        vo.setCustomerName(customer == null ? null : customer.getName());

        // 验收单：按 customer + accept_date 归月
        Acceptance query = new Acceptance();
        query.setCustomerId(customerId);
        query.setBeginAcceptDate(start);
        query.setEndAcceptDate(end);
        List<Acceptance> acceptances = acceptanceService.selectAcceptanceList(query);
        vo.setAcceptances(acceptances);

        // 下月调整单：按 customer + bill_month
        MonthAdjustment adjQuery = new MonthAdjustment();
        adjQuery.setCustomerId(customerId);
        adjQuery.setBillMonth(billMonth);
        List<MonthAdjustment> adjustments = monthAdjustmentMapper.selectList(adjQuery);
        vo.setAdjustments(adjustments);

        // 汇总：已提交验收实收 + 已提交调整单分项
        BigDecimal accepted = BigDecimal.ZERO;
        for (Acceptance a : acceptances) {
            if (AcceptanceStatus.SUBMITTED.getCode().equals(a.getStatus()) && a.getTotalAmount() != null) {
                accepted = accepted.add(a.getTotalAmount());
            }
        }
        BigDecimal recv = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (MonthAdjustment adj : adjustments) {
            if (MonthAdjustmentStatus.SUBMITTED.getCode().equals(adj.getStatus())) {
                recv = recv.add(nvl(adj.getReceivableAmount()));
                cost = cost.add(nvl(adj.getPurchaseCostAmount()));
            }
        }
        vo.setAcceptedAmount(accepted);
        vo.setAdjustmentReceivable(recv);
        vo.setAdjustmentCost(cost);
        vo.setAdjustmentNet(recv.subtract(cost));
        vo.setSettleAmount(accepted.add(vo.getAdjustmentNet()));
        return vo;
    }

    @Override
    @Transactional
    public MonthSettlement settle(Long customerId, String billMonth, String remark) {
        validate(customerId, billMonth);
        MonthSettlement exist = monthSettlementMapper.selectByCustomerAndMonth(customerId, billMonth);
        String operator = resolveOperator();
        MonthSettlement settlement;
        if (exist == null) {
            settlement = new MonthSettlement();
            settlement.setCustomerId(customerId);
            settlement.setBillMonth(billMonth);
            settlement.setStatus(1);
            settlement.setSettledBy(operator);
            settlement.setSettledTime(DateUtils.getNowDate());
            settlement.setRemark(remark);
            monthSettlementMapper.insert(settlement);
        } else {
            settlement = exist;
            if (Integer.valueOf(1).equals(exist.getStatus())) {
                throw new ServiceException("客户 " + customerId + " 的 " + billMonth + " 已月结，请勿重复结算");
            }
            MonthSettlement update = new MonthSettlement();
            update.setId(exist.getId());
            update.setStatus(1);
            update.setSettledBy(operator);
            update.setSettledTime(DateUtils.getNowDate());
            update.setRemark(remark);
            monthSettlementMapper.update(update);
            settlement.setStatus(1);
            settlement.setSettledBy(operator);
            settlement.setSettledTime(DateUtils.getNowDate());
        }
        log.info("[month settle] 客户 {} {} 已月结（操作人 {}）", customerId, billMonth, operator);
        return settlement;
    }

    @Override
    public boolean isSettled(Long customerId, String billMonth) {
        if (customerId == null || billMonth == null) {
            return false;
        }
        MonthSettlement s = monthSettlementMapper.selectByCustomerAndMonth(customerId, billMonth);
        return s != null && Integer.valueOf(1).equals(s.getStatus());
    }

    @Override
    public List<MonthSettlement> selectList(MonthSettlement query) {
        return monthSettlementMapper.selectList(query);
    }

    @Override
    public MonthSettlement getByCustomerAndMonth(Long customerId, String billMonth) {
        return monthSettlementMapper.selectByCustomerAndMonth(customerId, billMonth);
    }

    private void validate(Long customerId, String billMonth) {
        if (customerId == null) {
            throw new ServiceException("请选择月结客户");
        }
        if (billMonth == null || !billMonth.matches("\\d{4}-\\d{2}")) {
            throw new ServiceException("请填写结算月份（yyyy-MM）");
        }
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String resolveOperator() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}

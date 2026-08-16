package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.ReportRow;
import com.lin.distribution.dto.ReportVO;
import com.lin.distribution.mapper.ReportMapper;
import com.lin.distribution.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表Service业务层处理（DESIGN.md §10）
 * 销售日报按客户+配送点分组；客户对账单按验收单分组并合计。
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {
    private final ReportMapper reportMapper;

    @Override
    public List<ReportVO.DailySaleGroup> dailySale(LocalDate deliveryDate) {
        if (deliveryDate == null) {
            throw new ServiceException("配送日期不能为空");
        }
        List<ReportRow> rows = reportMapper.selectDailySaleRows(deliveryDate);
        Map<String, ReportVO.DailySaleGroup> groupMap = new LinkedHashMap<>();
        for (ReportRow row : rows) {
            String key = String.valueOf(row.getCustomerId()) + ":" + String.valueOf(row.getDeliveryPointId());
            ReportVO.DailySaleGroup group = groupMap.computeIfAbsent(key, k -> {
                ReportVO.DailySaleGroup g = new ReportVO.DailySaleGroup();
                g.setCustomerId(row.getCustomerId());
                g.setCustomerName(row.getCustomerName());
                g.setDeliveryPointId(row.getDeliveryPointId());
                g.setDeliveryPointName(row.getDeliveryPointName());
                g.setTotalActualAmount(BigDecimal.ZERO);
                g.setTotalLossQuantity(BigDecimal.ZERO);
                g.setTotalLossAmount(BigDecimal.ZERO);
                g.setItems(new ArrayList<>());
                return g;
            });
            BigDecimal actualAmount = zeroIfNull(row.getActualAmount());
            BigDecimal lossQuantity = zeroIfNull(row.getLossQuantity());
            BigDecimal unitPrice = zeroIfNull(row.getUnitPrice());
            group.setTotalActualAmount(group.getTotalActualAmount().add(actualAmount));
            group.setTotalLossQuantity(group.getTotalLossQuantity().add(lossQuantity));
            group.setTotalLossAmount(group.getTotalLossAmount().add(lossQuantity.multiply(unitPrice)));

            ReportVO.DailySaleItem item = new ReportVO.DailySaleItem();
            item.setProductName(row.getProductName());
            item.setProductSpec(row.getProductSpec());
            item.setProductUnit(row.getProductUnit());
            item.setActualQuantity(row.getActualQuantity());
            item.setLossQuantity(row.getLossQuantity());
            item.setUnitPrice(row.getUnitPrice());
            item.setActualAmount(row.getActualAmount());
            group.getItems().add(item);
        }
        return new ArrayList<>(groupMap.values());
    }

    @Override
    public ReportVO.CustomerStatement customerStatement(Long customerId, LocalDate beginDate, LocalDate endDate) {
        if (customerId == null) {
            throw new ServiceException("客户不能为空");
        }
        if (beginDate == null || endDate == null) {
            throw new ServiceException("日期范围不能为空");
        }
        if (beginDate.isAfter(endDate)) {
            throw new ServiceException("起始日期不能晚于结束日期");
        }
        List<ReportRow> rows = reportMapper.selectStatementRows(customerId, beginDate, endDate);

        ReportVO.CustomerStatement statement = new ReportVO.CustomerStatement();
        statement.setCustomerId(customerId);
        statement.setBeginDate(beginDate);
        statement.setEndDate(endDate);
        statement.setTotalAmount(BigDecimal.ZERO);
        statement.setAcceptances(new ArrayList<>());
        if (rows.isEmpty()) {
            statement.setCustomerName(null);
            return statement;
        }
        statement.setCustomerName(rows.get(0).getCustomerName());

        Map<Long, ReportVO.StatementAcceptance> acceptanceMap = new LinkedHashMap<>();
        for (ReportRow row : rows) {
            ReportVO.StatementAcceptance acceptance = acceptanceMap.computeIfAbsent(row.getAcceptanceId(), id -> {
                ReportVO.StatementAcceptance a = new ReportVO.StatementAcceptance();
                a.setAcceptanceId(row.getAcceptanceId());
                a.setCode(row.getAcceptanceCode());
                a.setAcceptDate(row.getAcceptDate());
                a.setDeliveryCode(row.getDeliveryCode());
                a.setTotalAmount(zeroIfNull(row.getAcceptanceTotalAmount()));
                a.setItems(new ArrayList<>());
                statement.setTotalAmount(statement.getTotalAmount().add(a.getTotalAmount()));
                return a;
            });
            ReportVO.StatementItem item = new ReportVO.StatementItem();
            item.setProductName(row.getProductName());
            item.setProductSpec(row.getProductSpec());
            item.setProductUnit(row.getProductUnit());
            item.setActualQuantity(row.getActualQuantity());
            item.setUnitPrice(row.getUnitPrice());
            item.setActualAmount(row.getActualAmount());
            acceptance.getItems().add(item);
        }
        statement.setAcceptances(new ArrayList<>(acceptanceMap.values()));
        return statement;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

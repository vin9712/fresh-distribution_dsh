package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.dto.SaleOrderActualDraftDTO;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.service.SaleOrderDetailService;

/**
 * 销售订单详情Service业务层处理
 *
 * @author lin
 * @date 2024-11-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaleOrderDetailServiceImpl implements SaleOrderDetailService {
    private final SaleOrderDetailMapper saleOrderDetailMapper;
    private final SaleOrderMapper saleOrderMapper;

    /**
     * 查询销售订单详情
     *
     * @param id 销售订单详情主键
     * @return 销售订单详情
     */
    @Override
    public SaleOrderDetail selectSaleOrderDetailById(Long id) {
        return saleOrderDetailMapper.selectSaleOrderDetailById(id);
    }

    /**
     * 查询销售订单详情列表
     *
     * @param saleOrderDetail 销售订单详情
     * @return 销售订单详情
     */
    @Override
    public List<SaleOrderDetail> selectSaleOrderDetailList(SaleOrderDetail saleOrderDetail) {
        return saleOrderDetailMapper.selectSaleOrderDetailList(saleOrderDetail);
    }

    /**
     * 新增销售订单详情
     *
     * @param saleOrderDetail 销售订单详情
     * @return 结果
     */
    @Override
    public int insertSaleOrderDetail(SaleOrderDetail saleOrderDetail) {
        saleOrderDetail.setCreateTime(DateUtils.getNowDate());
        return saleOrderDetailMapper.insertSaleOrderDetail(saleOrderDetail);
    }

    /**
     * 修改销售订单详情
     *
     * @param saleOrderDetail 销售订单详情
     * @return 结果
     */
    @Override
    public int updateSaleOrderDetail(SaleOrderDetail saleOrderDetail) {
        saleOrderDetail.setUpdateTime(DateUtils.getNowDate());
        return saleOrderDetailMapper.updateSaleOrderDetail(saleOrderDetail);
    }

    /**
     * 批量删除销售订单详情
     *
     * @param ids 需要删除的销售订单详情主键
     * @return 结果
     */
    @Override
    public int deleteSaleOrderDetailByIds(Long[] ids) {
        return saleOrderDetailMapper.deleteSaleOrderDetailByIds(ids);
    }

    /**
     * 删除销售订单详情信息
     *
     * @param id 销售订单详情主键
     * @return 结果
     */
    @Override
    public int deleteSaleOrderDetailById(Long id) {
        return saleOrderDetailMapper.deleteSaleOrderDetailById(id);
    }

    /**
     * 常用商品统计：近 N 天下单频率最高的 SKU（录单页"常用"面板）
     */
    @Override
    public List<SaleOrderDetail> selectFrequentSkuList(Long customerId, Integer days, Integer limit, Long deliveryPointId) {
        if (customerId == null) {
            return Collections.emptyList();
        }
        int d = (days == null || days <= 0) ? 30 : Math.min(days, 90);
        int lim = (limit == null || limit <= 0) ? 20 : Math.min(limit, 50);
        LocalDateTime startTime = LocalDate.now().minusDays(d).atStartOfDay();
        return saleOrderDetailMapper.selectFrequentSkuList(customerId, startTime, lim, deliveryPointId);
    }

    /**
     * 批量保存实收草稿：仅写 actual_num / loss_reason，不改订单状态。
     * 草稿阶段不强制损耗原因（确认验收时才强校验）。
     */
    @Override
    @Transactional
    public int saveActualDraft(SaleOrderActualDraftDTO request) {
        if (request == null || request.getOrderId() == null || CollectionUtils.isEmpty(request.getItems())) {
            throw new ServiceException("实收草稿数据不能为空");
        }
        Long orderId = request.getOrderId();
        SaleOrder order = saleOrderMapper.selectSaleOrderById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        if (!SaleOrderStatus.DELIVERED.getCode().equals(order.getStatus())) {
            throw new ServiceException("仅已配送状态的订单可填写实收草稿");
        }

        Map<Long, SaleOrderDetail> detailMap = saleOrderDetailMapper.selectSaleOrderDetailList(
                        SaleOrderDetail.builder().orderId(orderId).build()).stream()
                .collect(Collectors.toMap(SaleOrderDetail::getId, Function.identity()));

        int rows = 0;
        for (SaleOrderActualDraftDTO.Item item : request.getItems()) {
            if (item.getDetailId() == null || !detailMap.containsKey(item.getDetailId())) {
                throw new ServiceException("明细行不存在或不属于该订单");
            }
            SaleOrderDetail update = new SaleOrderDetail();
            update.setId(item.getDetailId());
            // 实收为空视为与下单数一致
            BigDecimal actual = item.getActualNum() != null ? item.getActualNum()
                    : Optional.ofNullable(detailMap.get(item.getDetailId()).getNum()).orElse(BigDecimal.ZERO);
            if (actual.compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("实收数量不能为负");
            }
            update.setActualNum(actual);
            update.setLossReason(StringUtils.trimToNull(item.getLossReason()));
            // 草稿阶段不落结算金额
            update.setActualAmount(null);
            rows += saleOrderDetailMapper.updateActualBatch(update);
        }
        return rows;
    }

    /**
     * 批量确认验收：空实收行自动按下单数计；实收<下单数必填损耗原因；
     * 重算 actual_price/actual_amount 后整批置为已验收(3)。
     */
    @Override
    @Transactional
    public int confirmAcceptance(List<Long> orderIds) {
        if (CollectionUtils.isEmpty(orderIds)) {
            throw new ServiceException("请选择要验收的订单");
        }
        List<Long> distinctIds = orderIds.stream().distinct().collect(Collectors.toList());
        List<SaleOrder> orders = saleOrderMapper.selectSaleOrderByIdIn(distinctIds);
        if (orders.size() != distinctIds.size()) {
            throw new ServiceException("部分订单不存在或已删除，请刷新后重试");
        }
        String invalidCodes = orders.stream()
                .filter(o -> !SaleOrderStatus.DELIVERED.getCode().equals(o.getStatus()))
                .map(SaleOrder::getCode)
                .collect(Collectors.joining("、"));
        if (StringUtils.isNotEmpty(invalidCodes)) {
            throw new ServiceException("以下订单不是已配送状态：" + invalidCodes);
        }

        for (SaleOrder order : orders) {
            List<SaleOrderDetail> details = saleOrderDetailMapper.selectSaleOrderDetailList(
                    SaleOrderDetail.builder().orderId(order.getId()).build());
            if (CollectionUtils.isEmpty(details)) {
                throw new ServiceException("订单【" + order.getCode() + "】明细为空，无法验收");
            }
            for (SaleOrderDetail detail : details) {
                // 空实收行按下单数计；有记录的以记录为准
                BigDecimal num = Optional.ofNullable(detail.getNum()).orElse(BigDecimal.ZERO);
                BigDecimal actual = detail.getActualNum() != null ? detail.getActualNum() : num;
                if (actual.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ServiceException("订单【" + order.getCode() + "】商品【"
                            + detail.getProductName() + "】实收数量不能为负");
                }
                // 沿用现有校验：实收 < 下单数必须填损耗原因
                if (actual.compareTo(num) < 0 && StringUtils.isBlank(detail.getLossReason())) {
                    throw new ServiceException("订单【" + order.getCode() + "】商品【"
                            + detail.getProductName() + "】实收小于下单数量，必须选择损耗原因");
                }
                BigDecimal price = Optional.ofNullable(detail.getProductPrice()).orElse(BigDecimal.ZERO);
                SaleOrderDetail update = new SaleOrderDetail();
                update.setId(detail.getId());
                update.setActualNum(actual);
                // 非负差异保留空原因避免脏数据
                update.setLossReason(actual.compareTo(num) < 0 ? StringUtils.trimToNull(detail.getLossReason()) : null);
                update.setActualPrice(price);
                // 实收金额 = 单价 × 实收数量，作为客户结算唯一依据
                update.setActualAmount(NumberUtils.toScaledBigDecimal(price.multiply(actual), 2, RoundingMode.HALF_UP));
                saleOrderDetailMapper.updateActualBatch(update);
            }
            // 整单置为已验收
            order.setStatus(SaleOrderStatus.ACCEPTED.getCode());
            saleOrderMapper.updateSaleOrder(order);
        }
        return orders.size();
    }

    /**
     * 撤销验收：状态回退 3→2 并清空本次验收产生的实收数据（默认清空）。
     * 已进入已结算状态的订单禁止回退。
     */
    @Override
    @Transactional
    public int revokeAcceptance(List<Long> orderIds) {
        if (CollectionUtils.isEmpty(orderIds)) {
            throw new ServiceException("请选择要撤销验收的订单");
        }
        List<Long> distinctIds = orderIds.stream().distinct().collect(Collectors.toList());
        List<SaleOrder> orders = saleOrderMapper.selectSaleOrderByIdIn(distinctIds);
        if (orders.size() != distinctIds.size()) {
            throw new ServiceException("部分订单不存在或已删除，请刷新后重试");
        }
        String settledCodes = orders.stream()
                .filter(o -> SaleOrderStatus.SETTLED.getCode().equals(o.getStatus()))
                .map(SaleOrder::getCode)
                .collect(Collectors.joining("、"));
        if (StringUtils.isNotEmpty(settledCodes)) {
            throw new ServiceException("以下订单已结算，禁止撤销验收：" + settledCodes);
        }
        String invalidCodes = orders.stream()
                .filter(o -> !SaleOrderStatus.ACCEPTED.getCode().equals(o.getStatus()))
                .map(SaleOrder::getCode)
                .collect(Collectors.joining("、"));
        if (StringUtils.isNotEmpty(invalidCodes)) {
            throw new ServiceException("以下订单不是已验收状态：" + invalidCodes);
        }

        for (SaleOrder order : orders) {
            saleOrderDetailMapper.clearActualByOrderId(order.getId());
            order.setStatus(SaleOrderStatus.DELIVERED.getCode());
            saleOrderMapper.updateSaleOrder(order);
        }
        return orders.size();
    }
}

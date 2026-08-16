package com.lin.distribution.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.OrderAdjustment;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.OrderAdjustmentCreateDTO;
import com.lin.distribution.mapper.OrderAdjustmentMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.OrderAdjustmentService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 订单加退换调整Service业务层处理
 *
 * @author dsh
 */
@Service
public class OrderAdjustmentServiceImpl implements OrderAdjustmentService {

    @Autowired
    private OrderAdjustmentMapper orderAdjustmentMapper;

    @Autowired
    private SaleOrderMapper saleOrderMapper;

    @Autowired
    private SaleOrderDetailMapper saleOrderDetailMapper;

    @Override
    @Transactional
    public void createAdjustment(OrderAdjustmentCreateDTO dto) {
        // 参数校验
        if (dto == null || dto.getOrderId() == null) {
            throw new ServiceException("订单id不能为空");
        }
        if (dto.getType() == null || !(dto.getType() == 1 || dto.getType() == 2 || dto.getType() == 3)) {
            throw new ServiceException("调整类型不正确");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new ServiceException("调整明细不能为空");
        }

        // 查订单并校验状态：仅已配送(2)/已验收(3)可调整，已结算(4)拒绝
        SaleOrder order = saleOrderMapper.selectSaleOrderById(dto.getOrderId());
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        Integer status = order.getStatus();
        if (SaleOrderStatus.SETTLED.getCode().equals(status)) {
            throw new ServiceException("已结算订单不允许调整");
        }
        if (!SaleOrderStatus.DELIVERED.getCode().equals(status) && !SaleOrderStatus.ACCEPTED.getCode().equals(status)) {
            throw new ServiceException("仅已配送或已验收订单允许调整");
        }

        // 现有明细最大排序（新增行 sort = max+1）
        SaleOrderDetail query = new SaleOrderDetail();
        query.setOrderId(order.getId());
        List<SaleOrderDetail> details = saleOrderDetailMapper.selectSaleOrderDetailList(query);
        int maxSort = details.stream()
                .map(SaleOrderDetail::getSort)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        for (OrderAdjustmentCreateDTO.Item item : dto.getItems()) {
            if (item.getDeltaQuantity() == null) {
                throw new ServiceException("调整数量不能为空");
            }

            if (item.getOrderItemId() != null) {
                // 已有明细行：数量增减
                SaleOrderDetail detail = saleOrderDetailMapper.selectSaleOrderDetailById(item.getOrderItemId());
                if (detail == null) {
                    throw new ServiceException("订单明细不存在");
                }
                BigDecimal oldNum = detail.getNum() == null ? BigDecimal.ZERO : detail.getNum();
                BigDecimal newNum = oldNum.add(item.getDeltaQuantity());
                if (newNum.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ServiceException("数量不能为负");
                }
                SaleOrderDetail update = new SaleOrderDetail();
                update.setId(detail.getId());
                update.setNum(newNum);
                update.setExpectAmount(calcExpectAmount(newNum, detail.getProductPrice()));
                update.setUpdateTime(DateUtils.getNowDate());
                saleOrderDetailMapper.updateSaleOrderDetail(update);
            } else {
                // 新增明细行：数量必须为正
                if (item.getDeltaQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ServiceException("新增行数量必须大于0");
                }
                if (StringUtils.isBlank(item.getProductName())) {
                    throw new ServiceException("商品名称不能为空");
                }
                BigDecimal price = item.getProductPrice() == null ? BigDecimal.ZERO : item.getProductPrice();
                maxSort = maxSort + 1;
                SaleOrderDetail detail = SaleOrderDetail.builder()
                        .orderId(order.getId())
                        .customerId(order.getCustomerId())
                        .customerDeptId(order.getCustomerDeptId())
                        .skuId(item.getSkuId())
                        .orderCode(order.getCode())
                        .productName(item.getProductName())
                        .productUnit(item.getProductUnit())
                        .productPrice(price)
                        .productSpec(item.getProductSpec())
                        .num(item.getDeltaQuantity())
                        .expectAmount(calcExpectAmount(item.getDeltaQuantity(), price))
                        .actualPrice(BigDecimal.ZERO)
                        .actualNum(BigDecimal.ZERO)
                        .actualAmount(BigDecimal.ZERO)
                        .sort(maxSort)
                        .isDeleted(Boolean.FALSE)
                        .version(0)
                        .build();
                detail.setCreateTime(DateUtils.getNowDate());
                saleOrderDetailMapper.insertSaleOrderDetail(detail);
            }
        }

        // 写调整主表
        OrderAdjustment adjustment = new OrderAdjustment();
        adjustment.setOrderId(order.getId());
        adjustment.setType(dto.getType());
        adjustment.setReason(dto.getReason());
        adjustment.setAdjustDate(dto.getAdjustDate() == null ? LocalDate.now() : dto.getAdjustDate());
        adjustment.setDetailJson(JSON.toJSONString(dto.getItems()));
        adjustment.setCreateTime(DateUtils.getNowDate());
        orderAdjustmentMapper.insertOrderAdjustment(adjustment);

        // 重算订单金额（明细 expect_amount 之和）并置 adjust_flag
        List<SaleOrderDetail> afterDetails = saleOrderDetailMapper.selectSaleOrderDetailList(query);
        BigDecimal totalAmount = afterDetails.stream()
                .map(d -> d.getExpectAmount() == null ? BigDecimal.ZERO : d.getExpectAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        orderAdjustmentMapper.updateOrderAdjustFlagAndAmount(order.getId(), totalAmount);
    }

    @Override
    public List<OrderAdjustment> selectByOrderId(Long orderId) {
        return orderAdjustmentMapper.selectOrderAdjustmentByOrderId(orderId);
    }

    @Override
    public OrderAdjustment selectById(Long id) {
        return orderAdjustmentMapper.selectOrderAdjustmentById(id);
    }

    private BigDecimal calcExpectAmount(BigDecimal num, BigDecimal price) {
        BigDecimal n = num == null ? BigDecimal.ZERO : num;
        BigDecimal p = price == null ? BigDecimal.ZERO : price;
        return NumberUtils.toScaledBigDecimal(n.multiply(p), 2, RoundingMode.HALF_UP);
    }
}

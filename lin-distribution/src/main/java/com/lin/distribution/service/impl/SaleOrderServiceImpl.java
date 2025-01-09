package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.constant.PrintTemplateType;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.PrintTask;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.SaleOrderCreateDTO;
import com.lin.distribution.dto.SaleOrderUpdateStatusDTO;
import com.lin.distribution.dto.print.DeliveryOrderPrintDTO;
import com.lin.distribution.dto.print.PrintObject;
import com.lin.distribution.mapper.PrintTaskMapper;
import com.lin.distribution.mapper.PrintTemplateMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.DeliveryOrderService;
import com.lin.distribution.service.SaleOrderService;
import com.lin.distribution.vo.SaleOrderDetailVo;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
    private final PrintTaskMapper printTaskMapper;
    private final PrintTemplateMapper printTemplateMapper;
    private final RedissonClient redissonClient;

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
     * 批量删除销售订单
     *
     * @param ids 需要删除的销售订单主键
     * @return 结果
     */
    @Override
    public int deleteSaleOrderByIds(Long[] ids) {
        return saleOrderMapper.deleteSaleOrderByIds(ids);
    }

    /**
     * 删除销售订单信息
     *
     * @param id 销售订单主键
     * @return 结果
     */
    @Override
    public int deleteSaleOrderById(Long id) {
        return saleOrderMapper.deleteSaleOrderById(id);
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
        // calc amount
        BigDecimal amount = orderDetails.stream().map(it -> {
            BigDecimal productNum = Optional.ofNullable(it.getNum()).orElse(BigDecimal.ZERO);
            BigDecimal productPrice = Optional.ofNullable(it.getProductPrice()).orElse(BigDecimal.ZERO);
            BigDecimal expectAmount = NumberUtils.toScaledBigDecimal(productNum.multiply(productPrice), 2, RoundingMode.HALF_UP);
            // set expectAmount
            it.setExpectAmount(expectAmount);
            // return to calc sum amount
            return expectAmount;
        }).reduce(BigDecimal.ZERO, BigDecimal::add);

        // insert order
        SaleOrder order = SaleOrder.builder()
                .customerId(customerId)
                .customerDeptId(customerDeptId)
                .code(orderCode)
                .deliveryDate(request.getDeliveryDate())
                .amount(amount)
                .status(SaleOrderStatus.NEW.getCode())
                .source(1)
                .type(1)
                .version(0)
                .isDeleted(Boolean.FALSE)
                .build();
        order.setRemark(request.getRemark());
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

        // increase orderCode
        generateOrderNo(true);

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
        // calc amount
        BigDecimal amount = orderDetails.stream().map(it -> {
            BigDecimal productNum = Optional.ofNullable(it.getNum()).orElse(BigDecimal.ZERO);
            BigDecimal productPrice = Optional.ofNullable(it.getProductPrice()).orElse(BigDecimal.ZERO);
            BigDecimal expectAmount = NumberUtils.toScaledBigDecimal(productNum.multiply(productPrice), 2, RoundingMode.HALF_UP);
            // set expectAmount
            it.setExpectAmount(expectAmount);
            // return to calc sum amount
            return expectAmount;
        }).reduce(BigDecimal.ZERO, BigDecimal::add);

        SaleOrder order = saleOrderMapper.selectSaleOrderById(orderId);

        // update order
        order.setDeliveryDate(request.getDeliveryDate());
        order.setAmount(amount);
        order.setRemark(request.getRemark());
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
    public List<SaleOrder> selectRecentOrderList(Long customerId, String keyword, Integer recentDays) {
        int days = Optional.ofNullable(recentDays).orElse(7);
        LocalDateTime createEndTime = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime createStartTime = LocalDate.now().minusDays(days).atStartOfDay();
        return saleOrderMapper.selectRecentOrderList(customerId, keyword, createStartTime, createEndTime);
    }

    @Override
    @Transactional
    public void updateSaleOrderStatus(SaleOrderUpdateStatusDTO request) {
        List<Long> orderIds = request.getOrderIds();
        SaleOrderStatus newStatus = SaleOrderStatus.fromCode(request.getStatus());
        List<SaleOrder> orders = saleOrderMapper.selectSaleOrderByIdIn(orderIds);

        // check new order status
        boolean checkNewStatus = false;
        switch (newStatus) {
            case NEW, DELIVERED ->
                    checkNewStatus = orders.stream().allMatch(it -> SaleOrderStatus.APPROVED.getCode().equals(it.getStatus()));
            case APPROVED ->
                    checkNewStatus = orders.stream().allMatch(it -> SaleOrderStatus.NEW.getCode().equals(it.getStatus()));
            case CHECKED ->
                    checkNewStatus = orders.stream().allMatch(it -> SaleOrderStatus.DELIVERED.getCode().equals(it.getStatus()));
            case FINISHED ->
                    checkNewStatus = orders.stream().allMatch(it -> SaleOrderStatus.CHECKED.getCode().equals(it.getStatus()));
        }
        if (!checkNewStatus) {
            throw new ServiceException("check new order status error");
        }

        // update order list
        for (SaleOrder order : orders) {
            order.setStatus(newStatus.getCode());
            saleOrderMapper.updateSaleOrder(order);
        }

        // if status is approved, create delivery order
//        if (newStatus == SaleOrderStatus.APPROVED) {
//            deliveryOrderService.createDeliveryOrder(orders);
//        // if status is new, clear delivery order & detail
//        } else if (newStatus == SaleOrderStatus.NEW) {
//            deliveryOrderService.clearDeliveryOrder(orders);
//        }
    }

    @Override
    public PrintTemplate getDeliveryPrintTemplate(Long orderId) {
        SaleOrder saleOrder = saleOrderMapper.selectSaleOrderById(orderId);
        if (saleOrder == null) {
            throw new ServiceException("sale order is null");
        }
        Long customerId = saleOrder.getCustomerId();

        PrintTemplate printTemplate = null;
        PrintTask printTask = printTaskMapper.selectLatestOneByOrderId(orderId);
        if (printTask != null) {
            printTemplate = printTemplateMapper.selectPrintTemplateById(printTask.getTemplateId());
        } else {
            printTemplate = printTemplateMapper.selectOneByCustomerIdAndType(customerId, PrintTemplateType.DELIVERY.getCode());
        }
        return printTemplate;
    }

    @Override
    public PrintObject<DeliveryOrderPrintDTO> buildDeliveryOrderPrintData(Long orderId, Long templateId) {
        SaleOrder saleOrder = saleOrderMapper.selectSaleOrderById(orderId);
        if (saleOrder == null) {
            throw new ServiceException("buildDeliveryOrderPrintData error, sale order is null");
        }

        PrintTemplate printTemplate = printTemplateMapper.selectPrintTemplateById(templateId);
        if (printTemplate == null) {
            throw new ServiceException("delivery template is null");
        }

        // 获取打印数据
        SaleOrderDetail od = new SaleOrderDetail();
        od.setOrderId(orderId);
        List<SaleOrderDetail> saleOrderDetails = saleOrderDetailMapper.selectSaleOrderDetailList(od);
        if (CollectionUtils.isEmpty(saleOrderDetails)) {
            throw new ServiceException("saleOrderDetails is null");
        }

        DeliveryOrderPrintDTO dto = DeliveryOrderPrintDTO.builder()
                .deliveryName("A company")
                .deliveryDate(saleOrder.getDeliveryDate())
                .customerDeptName(saleOrder.getCustomerName() + "-" + saleOrder.getCustomerDeptName())
                .table(saleOrderDetails)
                .build();

        return PrintObject.<DeliveryOrderPrintDTO>builder()
                .template(printTemplate.getContent())
                .data(dto)
                .build();
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
                throw new ServiceException("sale order no existed");
            }
        } else {
            SaleOrder saleOrder = saleOrderMapper.selectSaleOrderById(orderId);
            if (!SaleOrderStatus.NEW.getCode().equals(saleOrder.getStatus())) {
                throw new ServiceException("order status must be NEW");
            }
        }
    }

    private String generateOrderNo(Boolean refresh) {
        return generateOrderNo(refresh, null);
    }

    private String generateOrderNo(Boolean refresh, String currentCode) {
        String date = DateUtils.dateTime();
        String prefix = "XD" + date;
        RMap<String, Integer> rMap = redissonClient.getMap("saleOrderNo");
        // get current redis seq
        int redisSeq = rMap.getOrDefault(date, 0);
        String redisQuoteCode = prefix + String.format("%05d", redisSeq);
        // if current code = redis code, return
        if (StringUtils.equals(redisQuoteCode, currentCode)) {
            return redisQuoteCode;
        }

        int seqNbr = BooleanUtils.isTrue(refresh) ? rMap.addAndGet(date, 1) : redisSeq;
        String seqNbrStr = String.format("%05d", seqNbr);
        return prefix + seqNbrStr;
    }
}

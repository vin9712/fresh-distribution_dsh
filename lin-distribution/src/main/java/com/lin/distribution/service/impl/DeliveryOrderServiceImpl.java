package com.lin.distribution.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.lin.common.utils.DateUtils;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.service.DeliveryOrderService;
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
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    private final RedissonClient redissonClient;

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
     * 查询送货单据列表
     *
     * @param deliveryOrder 送货单据
     * @return 送货单据
     */
    @Override
    public List<DeliveryOrder> selectDeliveryOrderList(DeliveryOrder deliveryOrder) {
        return deliveryOrderMapper.selectDeliveryOrderList(deliveryOrder);
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
     * 创建送货单
     *
     * @param orders
     */
    @Override
    @Transactional
    public void createDeliveryOrder(List<SaleOrder> orders) {
        if (CollectionUtils.isEmpty(orders) ||
                orders.stream().anyMatch(order -> !Objects.equals(order.getStatus(), SaleOrderStatus.APPROVED.getCode()))) {
            return;
        }

        // 按 客户+配送日期 分组, 按需新增送货单+详情
        Map<String, List<SaleOrder>> orderMap = orders.stream().collect(Collectors.groupingBy(it -> it.getCustomerId() + "_" + it.getDeliveryDate()));
        for (String key : orderMap.keySet()) {
            String[] keyArr = key.split("_");
            Long customerId = Long.valueOf(keyArr[0]);
            LocalDate deliveryDate = LocalDate.parse(keyArr[1]);

            // 根据客户ID和配送日期查询送货单
            DeliveryOrder d = new DeliveryOrder();
            d.setCustomerId(customerId);
            d.setDeliveryDate(deliveryDate);
            DeliveryOrder deliveryOrder = deliveryOrderMapper.selectDeliveryOrderList(d).stream().findFirst().orElse(null);

            if (deliveryOrder == null) {
                deliveryOrder = DeliveryOrder.builder()
                        .customerId(customerId)
                        .deliveryDate(deliveryDate)
                        .code(generateDeliveryOrderNo(true))
                        .status(DeliveryOrderStatus.PENDING.getCode())
                        .isDeleted(Boolean.FALSE)
                        .version(0)
                        .build();
                deliveryOrderMapper.insertDeliveryOrder(deliveryOrder);
            }
            Long deliveryId = deliveryOrder.getId();

            // 查询并新增送货单详情
            Set<Long> deliveryOrderIdList = deliveryOrderDetailMapper.selectListByDeliveryId(deliveryId).stream().map(DeliveryOrderDetail::getOrderId).collect(Collectors.toSet());
            List<SaleOrder> saleOrders = orderMap.get(key).stream().filter(it -> !deliveryOrderIdList.contains(it.getId())).toList();
            // batch add delivery order
            if (CollectionUtils.isNotEmpty(saleOrders)) {
                for (SaleOrder saleOrder : saleOrders) {
                    DeliveryOrderDetail deliveryOrderDetail = DeliveryOrderDetail.builder()
                            .deliveryId(deliveryId)
                            .customerId(saleOrder.getCustomerId())
                            .orderId(saleOrder.getId())
                            .orderCode(saleOrder.getCode())
                            .customerId(saleOrder.getCustomerId())
                            .customerDeptId(saleOrder.getCustomerDeptId())
                            .isPrint(Boolean.FALSE)
                            .isDeleted(Boolean.FALSE)
                            .version(0)
                            .build();
                    deliveryOrderDetailMapper.insertDeliveryOrderDetail(deliveryOrderDetail);
                }
            }
        }
    }

    private String generateDeliveryOrderNo(Boolean refresh) {
        return generateDeliveryOrderNo(refresh, null);
    }

    private String generateDeliveryOrderNo(Boolean refresh, String currentCode) {
        String date = DateUtils.dateTime();
        String prefix = "SH" + date;
        RMap<String, Integer> rMap = redissonClient.getMap("deliveryOrderNo");
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
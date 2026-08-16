package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.DeliveryOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
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
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    private final SaleOrderDetailMapper saleOrderDetailMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final BizCodeService bizCodeService;

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
     * 按配送日期生成送货单（DESIGN.md §7.2）
     * 粒度：客户 + 配送点 + 配送日期；明细按商品合并（不含订单号）。
     */
    @Override
    @Transactional
    public List<DeliveryOrder> generateByDeliveryDate(LocalDate deliveryDate) {
        if (deliveryDate == null) {
            throw new ServiceException("配送日期不能为空");
        }

        // 幂等守卫：该日期已存在有效送货单则拒绝重复生成
        DeliveryOrder query = new DeliveryOrder();
        query.setDeliveryDate(deliveryDate);
        if (CollectionUtils.isNotEmpty(deliveryOrderMapper.selectDeliveryOrderList(query))) {
            throw new ServiceException("该配送日期已生成送货单，请勿重复生成");
        }

        // 仅汇总已确认（CONFIRMED）订单明细
        List<SaleOrderDetail> aggregated = saleOrderDetailMapper.selectAggregatedByDeliveryDate(deliveryDate);
        if (CollectionUtils.isEmpty(aggregated)) {
            throw new ServiceException("该配送日期没有已确认的订单，无法生成送货单");
        }

        // 按 客户+配送点 分组生成送货单
        Map<String, List<SaleOrderDetail>> groupMap = aggregated.stream().collect(Collectors.groupingBy(
                it -> String.valueOf(it.getCustomerId()) + ":" + String.valueOf(it.getCustomerDeptId()),
                LinkedHashMap::new, Collectors.toList()));

        List<DeliveryOrder> created = new ArrayList<>();
        for (Map.Entry<String, List<SaleOrderDetail>> entry : groupMap.entrySet()) {
            List<SaleOrderDetail> rows = entry.getValue();
            SaleOrderDetail first = rows.get(0);

            DeliveryOrder deliveryOrder = DeliveryOrder.builder()
                    .customerId(first.getCustomerId())
                    .deliveryPointId(first.getCustomerDeptId())
                    .code(bizCodeService.nextDailyCode("deliveryOrder", "HS", 3))
                    .status(DeliveryOrderStatus.PENDING.getCode())
                    .printCount(0)
                    .deliveryDate(deliveryDate)
                    .isDeleted(Boolean.FALSE)
                    .version(0)
                    .build();
            deliveryOrder.setCreateTime(DateUtils.getNowDate());
            deliveryOrderMapper.insertDeliveryOrder(deliveryOrder);

            for (SaleOrderDetail row : rows) {
                BigDecimal price = row.getProductPrice() == null ? BigDecimal.ZERO : row.getProductPrice();
                BigDecimal num = row.getNum() == null ? BigDecimal.ZERO : row.getNum();
                DeliveryOrderDetail detail = DeliveryOrderDetail.builder()
                        .deliveryId(deliveryOrder.getId())
                        .customerId(row.getCustomerId())
                        .customerDeptId(row.getCustomerDeptId())
                        .orderCode("")
                        .skuId(row.getSkuId())
                        .productName(row.getProductName())
                        .productUnit(row.getProductUnit())
                        .productSpec(row.getProductSpec())
                        .num(num)
                        .price(price)
                        .amount(price.multiply(num))
                        .isPrint(Boolean.FALSE)
                        .isDeleted(Boolean.FALSE)
                        .version(0)
                        .build();
                detail.setCreateTime(DateUtils.getNowDate());
                deliveryOrderDetailMapper.insertDeliveryOrderDetail(detail);
            }
            created.add(deliveryOrder);
        }
        return created;
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
        deliveryOrder.setUpdateTime(DateUtils.getNowDate());
        deliveryOrderMapper.updateDeliveryOrder(deliveryOrder);
        return deliveryOrder;
    }

    /**
     * 标记送达：状态 → 已送达，同组已确认订单 → DELIVERED
     */
    @Override
    @Transactional
    public DeliveryOrder markDelivered(Long id) {
        DeliveryOrder deliveryOrder = deliveryOrderMapper.selectDeliveryOrderById(id);
        if (deliveryOrder == null) {
            throw new ServiceException("送货单不存在");
        }
        if (Objects.equals(deliveryOrder.getStatus(), DeliveryOrderStatus.DELIVERED.getCode())) {
            throw new ServiceException("送货单已送达，请勿重复操作");
        }
        deliveryOrder.setStatus(DeliveryOrderStatus.DELIVERED.getCode());
        deliveryOrder.setUpdateTime(DateUtils.getNowDate());
        deliveryOrderMapper.updateDeliveryOrder(deliveryOrder);

        // 送货单标记送达 → 同组订单进入 DELIVERED（DESIGN.md §7.1）
        saleOrderMapper.markDeliveredByDeliveryGroup(
                deliveryOrder.getCustomerId(),
                deliveryOrder.getDeliveryPointId(),
                deliveryOrder.getDeliveryDate(),
                SaleOrderStatus.CONFIRMED.getCode(),
                SaleOrderStatus.DELIVERED.getCode());
        return deliveryOrder;
    }
}

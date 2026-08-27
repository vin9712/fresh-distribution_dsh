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
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.DeliveryByOrdersDTO;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.DeliveryOrderService;
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
     * 按勾选订单生成送货单（Phase 2，销售订单列表页抽屉）
     * 粒度：客户 + 配送点 + 配送日期（可调整）；明细带 order_id/order_code（order_id 唯一键防重）。
     */
    @Override
    @Transactional
    public List<DeliveryOrder> generateByOrderIds(DeliveryByOrdersDTO dto) {
        if (dto == null || CollectionUtils.isEmpty(dto.getOrderIds())) {
            throw new ServiceException("请选择要生成送货单的订单");
        }
        List<Long> orderIds = dto.getOrderIds().stream().distinct().collect(Collectors.toList());

        // 校验：订单存在且均已确认
        List<SaleOrder> orders = saleOrderMapper.selectSaleOrderByIdIn(orderIds);
        if (orders.size() != orderIds.size()) {
            throw new ServiceException("部分订单不存在或已删除，请刷新列表后重试");
        }
        String invalidCodes = orders.stream()
                .filter(o -> !SaleOrderStatus.CONFIRMED.getCode().equals(o.getStatus()))
                .map(SaleOrder::getCode)
                .collect(Collectors.joining(","));
        if (StringUtils.isNotEmpty(invalidCodes)) {
            throw new ServiceException("以下订单不是审核状态，无法生成送货单：" + invalidCodes);
        }

        // 幂等：任一选中订单已存在送货单明细（t_delivery_order_detail.order_id 唯一）
        List<DeliveryOrderDetail> existed = deliveryOrderDetailMapper.selectListByOrderIdIn(orderIds);
        if (CollectionUtils.isNotEmpty(existed)) {
            Set<Long> existedOrderIds = existed.stream().map(DeliveryOrderDetail::getOrderId).collect(Collectors.toSet());
            String codes = orders.stream()
                    .filter(o -> existedOrderIds.contains(o.getId()))
                    .map(SaleOrder::getCode)
                    .collect(Collectors.joining(","));
            throw new ServiceException("以下订单已生成送货单，请勿重复生成：" + codes);
        }

        LocalDate deliveryDate = dto.getDeliveryDate() != null ? dto.getDeliveryDate() : orders.get(0).getDeliveryDate();

        List<SaleOrderDetail> aggregated = saleOrderDetailMapper.selectAggregatedByOrderIds(orderIds);
        if (CollectionUtils.isEmpty(aggregated)) {
            throw new ServiceException("所选订单没有已确认的明细，无法生成送货单");
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
                        .orderId(row.getOrderId())
                        .customerId(row.getCustomerId())
                        .customerDeptId(row.getCustomerDeptId())
                        .orderCode(row.getOrderCode() == null ? "" : row.getOrderCode())
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
     * 标记送达：状态 → 已送达，仅回写来源台账中的订单（S14/G2：IN 子查询，替代同组推断）
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

        // 仅回写本单来源分配命中的订单（DESIGN.md §4.2 回写矩阵）；同客户同日未进单订单不受影响
        saleOrderMapper.updateStatusByDeliveryId(
                id,
                SaleOrderStatus.CONFIRMED.getCode(),
                SaleOrderStatus.DELIVERED.getCode());
        return deliveryOrder;
    }
}

package com.lin.distribution.service.impl;

import java.util.List;
import java.util.Objects;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.DeliveryOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final SaleOrderMapper saleOrderMapper;

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

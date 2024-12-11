package com.lin.distribution.service.impl;

import java.util.List;

import com.lin.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.service.DeliveryOrderService;

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
}
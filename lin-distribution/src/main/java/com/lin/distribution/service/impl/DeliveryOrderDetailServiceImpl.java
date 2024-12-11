package com.lin.distribution.service.impl;

import java.util.List;

import com.lin.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.service.DeliveryOrderDetailService;

/**
 * 送货单详情Service业务层处理
 *
 * @author lin
 * @date 2024-12-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOrderDetailServiceImpl implements DeliveryOrderDetailService {
    private final DeliveryOrderDetailMapper deliveryOrderDetailMapper;

    /**
     * 查询送货单详情
     *
     * @param id 送货单详情主键
     * @return 送货单详情
     */
    @Override
    public DeliveryOrderDetail selectDeliveryOrderDetailById(Long id) {
        return deliveryOrderDetailMapper.selectDeliveryOrderDetailById(id);
    }

    /**
     * 查询送货单详情列表
     *
     * @param deliveryOrderDetail 送货单详情
     * @return 送货单详情
     */
    @Override
    public List<DeliveryOrderDetail> selectDeliveryOrderDetailList(DeliveryOrderDetail deliveryOrderDetail) {
        return deliveryOrderDetailMapper.selectDeliveryOrderDetailList(deliveryOrderDetail);
    }

    /**
     * 新增送货单详情
     *
     * @param deliveryOrderDetail 送货单详情
     * @return 结果
     */
    @Override
    public int insertDeliveryOrderDetail(DeliveryOrderDetail deliveryOrderDetail) {
        deliveryOrderDetail.setCreateTime(DateUtils.getNowDate());
        return deliveryOrderDetailMapper.insertDeliveryOrderDetail(deliveryOrderDetail);
    }

    /**
     * 修改送货单详情
     *
     * @param deliveryOrderDetail 送货单详情
     * @return 结果
     */
    @Override
    public int updateDeliveryOrderDetail(DeliveryOrderDetail deliveryOrderDetail) {
        deliveryOrderDetail.setUpdateTime(DateUtils.getNowDate());
        return deliveryOrderDetailMapper.updateDeliveryOrderDetail(deliveryOrderDetail);
    }

    /**
     * 批量删除送货单详情
     *
     * @param ids 需要删除的送货单详情主键
     * @return 结果
     */
    @Override
    public int deleteDeliveryOrderDetailByIds(Long[] ids) {
        return deliveryOrderDetailMapper.deleteDeliveryOrderDetailByIds(ids);
    }

    /**
     * 删除送货单详情信息
     *
     * @param id 送货单详情主键
     * @return 结果
     */
    @Override
    public int deleteDeliveryOrderDetailById(Long id) {
        return deliveryOrderDetailMapper.deleteDeliveryOrderDetailById(id);
    }
}

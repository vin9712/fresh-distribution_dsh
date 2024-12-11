package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.DeliveryOrderDetail;

/**
 * 送货单详情Mapper接口
 *
 * @author lin
 * @date 2024-12-11
 */
public interface DeliveryOrderDetailMapper {
    /**
     * 查询送货单详情
     *
     * @param id 送货单详情主键
     * @return 送货单详情
     */
    DeliveryOrderDetail selectDeliveryOrderDetailById(Long id);

    /**
     * 根据送货单ID查询送货单详情列表
     * @param deliveryId
     * @return
     */
    List<DeliveryOrderDetail> selectListByDeliveryId(Long deliveryId);

    /**
     * 查询送货单详情列表
     *
     * @param deliveryOrderDetail 送货单详情
     * @return 送货单详情集合
     */
    List<DeliveryOrderDetail> selectDeliveryOrderDetailList(DeliveryOrderDetail deliveryOrderDetail);

    /**
     * 新增送货单详情
     *
     * @param deliveryOrderDetail 送货单详情
     * @return 结果
     */
    int insertDeliveryOrderDetail(DeliveryOrderDetail deliveryOrderDetail);

    /**
     * 修改送货单详情
     *
     * @param deliveryOrderDetail 送货单详情
     * @return 结果
     */
    int updateDeliveryOrderDetail(DeliveryOrderDetail deliveryOrderDetail);

    /**
     * 删除送货单详情
     *
     * @param id 送货单详情主键
     * @return 结果
     */
    int deleteDeliveryOrderDetailById(Long id);

    /**
     * 批量删除送货单详情
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteDeliveryOrderDetailByIds(Long[] ids);
}

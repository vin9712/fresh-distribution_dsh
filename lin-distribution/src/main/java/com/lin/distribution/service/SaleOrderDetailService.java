package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.domain.SaleOrderDetail;

/**
 * 销售订单详情Service接口
 *
 * @author lin
 * @date 2024-11-23
 */
public interface SaleOrderDetailService {
    /**
     * 查询销售订单详情
     *
     * @param id 销售订单详情主键
     * @return 销售订单详情
     */
    SaleOrderDetail selectSaleOrderDetailById(Long id);

    /**
     * 查询销售订单详情列表
     *
     * @param saleOrderDetail 销售订单详情
     * @return 销售订单详情集合
     */
    List<SaleOrderDetail> selectSaleOrderDetailList(SaleOrderDetail saleOrderDetail);

    /**
     * 新增销售订单详情
     *
     * @param saleOrderDetail 销售订单详情
     * @return 结果
     */
    int insertSaleOrderDetail(SaleOrderDetail saleOrderDetail);

    /**
     * 修改销售订单详情
     *
     * @param saleOrderDetail 销售订单详情
     * @return 结果
     */
    int updateSaleOrderDetail(SaleOrderDetail saleOrderDetail);

    /**
     * 批量删除销售订单详情
     *
     * @param ids 需要删除的销售订单详情主键集合
     * @return 结果
     */
    int deleteSaleOrderDetailByIds(Long[] ids);

    /**
     * 删除销售订单详情信息
     *
     * @param id 销售订单详情主键
     * @return 结果
     */
    int deleteSaleOrderDetailById(Long id);
}

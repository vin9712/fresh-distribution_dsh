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

    /**
     * 常用商品统计：近 N 天下单频率最高的 SKU（录单页"常用"面板）
     *
     * @param customerId 客户ID（必填）
     * @param days       统计天数（默认 30，上限 90）
     * @param limit      返回条数（默认 20，上限 50）
     * @param deliveryPointId 配送点ID（白名单过滤：通用池∪本点专属；空=仅通用商品）
     * @return 按下单次数倒序的 SKU 列表
     */
    List<SaleOrderDetail> selectFrequentSkuList(Long customerId, Integer days, Integer limit, Long deliveryPointId);
}

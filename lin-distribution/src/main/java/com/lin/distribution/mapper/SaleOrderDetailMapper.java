package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.SaleOrderDetail;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 销售订单详情Mapper接口
 *
 * @author lin
 * @date 2024-11-23
 */
public interface SaleOrderDetailMapper {
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
     * 根据 orderId 删除销售订单详情
     *
     * @param orderId 销售订单主键
     * @return 结果
     */
    int deleteSaleOrderDetailByOrderId(Long orderId);


    /**
     * 删除销售订单详情
     *
     * @param id 销售订单明细主键
     * @return 结果
     */
    int deleteSaleOrderDetailById(Long id);

    /**
     * 批量删除销售订单详情
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteSaleOrderDetailByIds(Long[] ids);

    /**
     * 按配送日期聚合已确认订单明细（送货单生成口径）
     * 分组：客户 + 配送点 + SKU/品名/单位/规格/单价；数量求和。
     *
     * @param deliveryDate 配送日期
     * @return 聚合后的订单明细
     */
    List<SaleOrderDetail> selectAggregatedByDeliveryDate(java.time.LocalDate deliveryDate);

    /**
     * 按指定订单ID集合聚合已确认订单明细（送货单按勾选订单生成口径）
     * 分组：客户 + 配送点 + SKU/品名/单位/规格/单价；数量求和。
     *
     * @param orderIds 销售订单ID集合
     * @return 聚合后的订单明细
     */
    List<SaleOrderDetail> selectAggregatedByOrderIds(@Param("orderIds") java.util.Collection<Long> orderIds);

    /**
     * 按指定订单ID集合聚合已确认订单明细并关联品类名称（生成单据预览口径）
     * 分组：品类 + SKU/品名/单位/规格；临时商品（sku_id 空）归"临时商品"品类。
     *
     * @param orderIds 销售订单ID集合
     * @return 含 categoryName 的聚合明细
     */
    List<SaleOrderDetail> selectPreviewByOrderIds(@Param("orderIds") java.util.Collection<Long> orderIds);

    /**
     * 常用商品统计：近 N 天下单频率最高的 SKU（录单页"常用"面板）
     *
     * @param customerId 客户ID
     * @param startTime  统计起始时间
     * @param limit      返回条数上限
     * @return 按下单次数倒序的 SKU 列表
     */
    List<SaleOrderDetail> selectFrequentSkuList(@Param("customerId") Long customerId,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("limit") Integer limit,
                                                @Param("deliveryPointId") Long deliveryPointId);
}

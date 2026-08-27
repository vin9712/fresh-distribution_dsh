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

    /**
     * 写入单行实收数据（草稿保存 / 确认验收共用，服务层按行循环）
     * 仅更新 actual_num / loss_reason / actual_amount。
     *
     * @param item 含 id + actualNum + lossReason(+actualAmount) 的明细
     * @return 影响行数
     */
    int updateActualBatch(@Param("item") SaleOrderDetail item);

    /**
     * 清空订单全部实收数据（撤销验收，回退 3→2 时默认清空）
     *
     * @param orderId 销售订单ID
     * @return 影响行数
     */
    int clearActualByOrderId(@Param("orderId") Long orderId);

    /**
     * 订单是否存在于有效送货单的来源分配中（S14/G4/G6 统一判定，附录 A）
     *
     * <p>有效 = 来源分配未软删 且 所属送货单未软删且未作废（VOIDED）。
     * 作废释放（source_item 软删）或单据作废后即返回 false，订单解锁可撤回/可编辑。</p>
     *
     * @param orderId 销售订单ID
     * @return true=已被有效送货单占用
     */
    boolean existsValidAllocation(@Param("orderId") Long orderId);
}

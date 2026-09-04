package com.lin.distribution.mapper;

import java.util.List;
import java.time.LocalDate;

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
     * 验收应送行数据源（D-055）：查 客户+配送日期+配送点 的有效订单明细（已确认订单，含变更标记），
     * 按订单明细行展开（含加单/换货/退货标记行，按 sort 升序）。
     *
     * @param customerId     客户ID
     * @param customerDeptId 配送点ID
     * @param deliveryDate   配送日期
     * @return 订单明细行（含标记）
     */
    List<SaleOrderDetail> selectValidByCustomerPointDate(@Param("customerId") Long customerId,
                                                         @Param("customerDeptId") Long customerDeptId,
                                                         @Param("deliveryDate") LocalDate deliveryDate);

    /**
     * 点单视图数据源（D-055 只读口径）：与矩阵/配货一致取 已确认及以后（status&gt;=1）的订单明细，
     * 仅排除草稿单与已删单——验收后回看本天不应空表。
     *
     * @param customerId     客户ID
     * @param customerDeptId 配送点ID
     * @param deliveryDate   配送日期
     * @return 订单明细行（含标记，sort 升序）
     */
    List<SaleOrderDetail> selectValidByCustomerPointDateForView(@Param("customerId") Long customerId,
                                                                @Param("customerDeptId") Long customerDeptId,
                                                                @Param("deliveryDate") LocalDate deliveryDate);

    /**
     * 点单全点视图数据源（D-055 收尾）：与按点视图同口径（status&gt;=1）但不按点过滤，
     * 行附带配送点名称（coalesce 明细行点/订单点），供服务层按点分组出 tab。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 订单明细行（cd.code, cd.id, d.sort 升序）
     */
    List<SaleOrderDetail> selectValidByCustomerDateForView(@Param("customerId") Long customerId,
                                                           @Param("deliveryDate") LocalDate deliveryDate);

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
     * 按指定订单ID集合查有效订单行原始明细（撤回级联用，不聚合）
     *
     * <p>聚合改在 Java 侧完成：需要保留 订单行→订单 映射以落 t_delivery_source_item 台账，
     * SQL GROUP BY 表达不了这个映射（DESIGN.md §5.1 删除说明）。</p>
     *
     * @param orderIds 销售订单ID集合
     * @return 原始订单行（按 order_id, sort, id 排序）
     */
    List<SaleOrderDetail> selectValidByOrderIdIn(@Param("orderIds") java.util.Collection<Long> orderIds);

    /**
     * D-055 点单验收回写：按明细ID集合反查（取 order_id 等归属字段）。
     *
     * @param ids 订单明细ID集合（非空）
     * @return 明细行（含 order_id）
     */
    List<SaleOrderDetail> selectByIdIn(@Param("ids") java.util.Collection<Long> ids);

    /**
     * 生成单据预览：按指定订单ID集合聚合已确认订单明细并关联品类名称
     * 分组：品类 + SKU/品名/单位/规格；临时商品（sku_id 空）归“临时商品”品类。
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

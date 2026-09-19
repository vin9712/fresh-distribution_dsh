package com.lin.distribution.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.lin.distribution.domain.SaleOrder;
import org.apache.ibatis.annotations.Param;

/**
 * 销售订单Mapper接口
 *
 * @author lin
 * @date 2024-11-23
 */
public interface SaleOrderMapper {
    /**
     * 查询销售订单
     *
     * @param id 销售订单主键
     * @return 销售订单
     */
    SaleOrder selectSaleOrderById(Long id);

    /**
     * 批量查询销售订单
     *
     * @param ids 销售订单主键
     * @return 销售订单
     */
    List<SaleOrder> selectSaleOrderByIdIn(List<Long> ids);

    /**
     * 根据订单编号查询订单
     * @param orderCode 订单编号
     * @return
     */
    SaleOrder selectSaleOrderByCode(String orderCode);

    /**
     * 查询销售订单列表
     *
     * @param saleOrder 销售订单
     * @return 销售订单集合
     */
    List<SaleOrder> selectSaleOrderList(SaleOrder saleOrder);

    /**
     * 客户视角分组聚合分页（D-064）：一行 = 客户 + 配送日期
     *
     * <p>筛选条件与 {@link #selectSaleOrderList} 逐条对齐，仅分组口径不同；
     * 分页单位 = 客户行，故必须在 SQL 层 group by（前端分页会切断同一客户）。</p>
     *
     * @param saleOrder 销售订单（复用其筛选字段）
     * @return 客户维度聚合行集合
     */
    List<com.lin.distribution.vo.SaleCustomerPageVO> selectCustomerPage(SaleOrder saleOrder);

    /**
     * 新增销售订单
     *
     * @param saleOrder 销售订单
     * @return 结果
     */
    int insertSaleOrder(SaleOrder saleOrder);

    /**
     * 修改销售订单
     *
     * @param saleOrder 销售订单
     * @return 结果
     */
    int updateSaleOrder(SaleOrder saleOrder);

    /**
     * 删除销售订单
     *
     * @param id 销售订单主键
     * @return 结果
     */
    int deleteSaleOrderById(Long id);

    /**
     * 批量删除销售订单
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteSaleOrderByIds(Long[] ids);

    /**
     * 获取最近创建订单列表
     *
     * @param customerId
     * @param keyword
     * @param createStartTime
     * @param createEndTime
     * @return
     */
    List<SaleOrder> selectRecentOrderList(@Param("customerId") Long customerId,
                                          @Param("keyword") String keyword,
                                          @Param("createStartTime") LocalDateTime createStartTime,
                                          @Param("createEndTime") LocalDateTime createEndTime,
                                          @Param("statuses") List<Integer> statuses);

    /**
     * 按订单编号批量查询（草稿箱陈旧判定用：只取 id/code/status/update_time，不做关联子查询）
     */
    List<SaleOrder> selectSaleOrderByCodes(@Param("codes") List<String> codes);

    /**
     * 按送货单来源回写订单状态（S14/G2/G3 修复：只动来源订单，废除按客户+日期推断）
     *
     * <p>影响面 = t_delivery_source_item 中该送货单的有效分配去重后的订单集合（DESIGN.md §4.2 回写矩阵）。
     * 同客户同日未进单的订单不会被误更新。</p>
     *
     * @param deliveryId 送货单ID
     * @param fromStatus 原状态（仅该状态会被更新）
     * @param toStatus   目标状态
     * @return 更新行数
     */
    int updateStatusByDeliveryId(@Param("deliveryId") Long deliveryId,
                                 @Param("fromStatus") Integer fromStatus,
                                 @Param("toStatus") Integer toStatus);

    /**
     * D-055 点单验收回写：按订单ID集合精确改状态（仅 fromStatus 命中才更新）。
     * 点单验收无送货单，来源订单由验收行的 sale_order_detail_id 反查得到。
     *
     * @param orderIds   订单ID集合（非空）
     * @param fromStatus 原状态
     * @param toStatus   目标状态
     * @return 更新行数
     */
    int updateStatusByIds(@Param("orderIds") java.util.Collection<Long> orderIds,
                          @Param("fromStatus") Integer fromStatus,
                          @Param("toStatus") Integer toStatus);

    /**
     * 查询同配送点+同日期的草稿订单（新增订单防重复提示用：选中客户后检测是否已有可继续添加的草稿）
     *
     * @param customerDeptId 配送点ID
     * @param deliveryDate   配送日期
     * @param shiftCode      班次过滤值（null=不按班次过滤；启用班次的客户传归一化后的班次）
     * @param defaultShift   无班次历史单归入的班次（业务口径：归白班），用于同口径匹配
     * @return 草稿订单集合（通常最多1个，按创建时间倒序取最新）
     */
    List<SaleOrder> selectExistingDraftOrder(@Param("customerDeptId") Long customerDeptId,
                                             @Param("deliveryDate") LocalDate deliveryDate,
                                             @Param("shiftCode") String shiftCode,
                                             @Param("defaultShift") String defaultShift);
}

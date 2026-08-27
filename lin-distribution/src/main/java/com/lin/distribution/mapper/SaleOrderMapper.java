package com.lin.distribution.mapper;

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
                                          @Param("createEndTime") LocalDateTime createEndTime);

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
}

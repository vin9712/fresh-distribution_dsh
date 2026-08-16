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
     * 送货单标记送达：同组（客户+配送点+配送日）已确认订单批量进入 DELIVERED
     *
     * @param customerId    客户ID
     * @param customerDeptId 配送点ID（可为空）
     * @param deliveryDate  配送日期
     * @param fromStatus    原状态
     * @param toStatus      目标状态
     * @return 更新行数
     */
    int markDeliveredByDeliveryGroup(@Param("customerId") Long customerId,
                                     @Param("customerDeptId") Long customerDeptId,
                                     @Param("deliveryDate") java.time.LocalDate deliveryDate,
                                     @Param("fromStatus") Integer fromStatus,
                                     @Param("toStatus") Integer toStatus);
}

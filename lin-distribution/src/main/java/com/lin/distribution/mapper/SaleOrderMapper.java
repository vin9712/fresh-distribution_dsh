package com.lin.distribution.mapper;

import java.time.LocalDate;
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
     * 获取最近订单列表
     *
     * @param customerId
     * @param keyword
     * @param deliveryStartDate
     * @param deliveryEndDate
     * @return
     */
    List<SaleOrder> selectRecentOrderList(@Param("customerId") Long customerId,
                                          @Param("keyword") String keyword,
                                          @Param("deliveryStartDate") LocalDate deliveryStartDate,
                                          @Param("deliveryEndDate") LocalDate deliveryEndDate);
}

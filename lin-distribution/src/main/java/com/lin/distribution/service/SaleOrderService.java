package com.lin.distribution.service;

import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.dto.SaleOrderCreateDTO;
import jakarta.validation.constraints.Max;

import java.util.List;

/**
 * 销售订单Service接口
 *
 * @author lin
 * @date 2024-11-23
 */
public interface SaleOrderService {
    /**
     * 查询销售订单
     *
     * @param id 销售订单主键
     * @return 销售订单
     */
    SaleOrder selectSaleOrderById(Long id);

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
     * 批量删除销售订单
     *
     * @param ids 需要删除的销售订单主键集合
     * @return 结果
     */
    int deleteSaleOrderByIds(Long[] ids);

    /**
     * 删除销售订单信息
     *
     * @param id 销售订单主键
     * @return 结果
     */
    int deleteSaleOrderById(Long id);

    /**
     * 生成销售订单号
     * @param refresh
     * @param currentCode
     * @return
     */
    String generateSaleOrderNo(Boolean refresh, String currentCode);

    /**
     * 创建销售订单+详情
     * @param request
     * @return
     */
    SaleOrder createSaleOrder(SaleOrderCreateDTO request);

    /**
     * 更新销售订单+详情
     * @param request
     * @return
     */
    SaleOrder updateSaleOrderWithDetails(SaleOrderCreateDTO request);

    /**
     * 查询最近销售订单
     * @return
     */
    List<SaleOrder> selectRecentOrderList(Long customerId, String keyword, Integer recentDays);
}

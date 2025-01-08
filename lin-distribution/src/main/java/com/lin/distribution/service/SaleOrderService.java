package com.lin.distribution.service;

import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.dto.print.DeliveryOrderPrintDTO;
import com.lin.distribution.dto.print.PrintObject;
import com.lin.distribution.dto.SaleOrderCreateDTO;
import com.lin.distribution.dto.SaleOrderUpdateStatusDTO;

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

    /**
     * 批量更新订单状态
     * @param request
     */
    void updateSaleOrderStatus(SaleOrderUpdateStatusDTO request);

    /**
     * 根据订单ID获取送货单打印模板
     * 尝试从上一次打印任务中获取 template，否则取最近更新的送货单模板
     * @param orderId
     * @return
     */
    PrintTemplate getDeliveryPrintTemplate(Long orderId);

    /**
     * 构造送货单打印数据
     * @param orderId
     * @return
     */
    PrintObject<DeliveryOrderPrintDTO> buildDeliveryOrderPrintData(Long orderId, Long templateId);
}

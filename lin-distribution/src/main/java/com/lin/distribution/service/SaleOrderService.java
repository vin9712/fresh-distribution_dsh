package com.lin.distribution.service;

import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.dto.SaleGeneratePreviewVO;
import com.lin.distribution.dto.SaleOrderCreateDTO;
import com.lin.distribution.dto.SaleOrderUpdateStatusDTO;
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

    /**
     * 批量更新订单状态
     * @param request
     */
    void updateSaleOrderStatus(SaleOrderUpdateStatusDTO request);

    /**
     * 生成单据前汇总预览（销售订单列表页抽屉第一步，Phase 2）
     * 校验订单均为已确认，按品类分组聚合明细并返回选中订单头信息。
     *
     * @param orderIds 选中的销售订单ID集合
     * @return 按品类分组的汇总预览
     */
    SaleGeneratePreviewVO generatePreview(List<Long> orderIds);

    /**
     * 查询同配送点+同日期的草稿订单（新增订单时，选中客户后检测是否已有可继续添加的草稿）
     *
     * @param customerDeptId 配送点ID
     * @param deliveryDate   配送日期
     * @return 最新一条草稿订单；无则返回 null
     */
    SaleOrder findExistingDraftOrder(Long customerDeptId, java.time.LocalDate deliveryDate);
}

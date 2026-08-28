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

    /**
     * 查“遗漏订单”集 O_missed（S14/T3 统一生成服务，DESIGN.md §5.1 步骤① / 附录 A）
     *
     * <p>口径：CONFIRMED 且未进入任何有效送货单。有效判定两路：
     * ① 新模型——t_delivery_source_item 有效分配（作废释放/软删即视为遗漏）；
     * ② 历史兼容——S14 前生成的送货单无台账，按 t_delivery_order_detail.order_id 直挂且所属单未作废判定。
     * 判定②防止历史单与新单并存时同一订单被双重配送。</p>
     *
     * @param customerId   客户ID（可空=null 时不限客户，generateForDate 全日期扫描用）
     * @param deliveryDate 配送日期（必填）
     * @return 遗漏订单集合
     */
    List<SaleOrder> selectMissedConfirmedOrders(@Param("customerId") Long customerId,
                                                @Param("deliveryDate") LocalDate deliveryDate);

    /**
     * 查该客户当日全部已确认订单（S14/T3 作废重建分支 D-022：重建覆盖全部已确认遗漏+原订单，而非拼接）
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 已确认订单集合
     */
    List<SaleOrder> selectConfirmedByCustomerAndDate(@Param("customerId") Long customerId,
                                                     @Param("deliveryDate") LocalDate deliveryDate);

    /**
     * 查询同配送点+同日期的草稿订单（新增订单防重复提示用：选中客户后检测是否已有可继续添加的草稿）
     *
     * @param customerDeptId 配送点ID
     * @param deliveryDate   配送日期
     * @return 草稿订单集合（通常最多1个，按创建时间倒序取最新）
     */
    List<SaleOrder> selectExistingDraftOrder(@Param("customerDeptId") Long customerDeptId,
                                             @Param("deliveryDate") LocalDate deliveryDate);
}

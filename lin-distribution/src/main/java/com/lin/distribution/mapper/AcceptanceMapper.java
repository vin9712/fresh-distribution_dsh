package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.Acceptance;

/**
 * 验收单Mapper接口
 *
 * @author dsh
 */
public interface AcceptanceMapper {
    /**
     * 查询验收单
     *
     * @param id 验收单主键
     * @return 验收单
     */
    Acceptance selectAcceptanceById(Long id);

    /**
     * 查询验收单列表
     *
     * @param acceptance 验收单
     * @return 验收单集合
     */
    List<Acceptance> selectAcceptanceList(Acceptance acceptance);

    /**
     * 统计指定送货单已存在的验收单数量（一单一验守卫）
     *
     * @param deliveryOrderId 送货单ID
     * @return 数量
     */
    int countByDeliveryOrderId(Long deliveryOrderId);

    /**
     * OA：按来源订单查订单维度验收单（最新 1 张，一订单一验定位用）
     *
     * @param saleOrderId 来源销售订单ID
     * @return 验收单，无则 null
     */
    Acceptance selectBySaleOrder(@org.apache.ibatis.annotations.Param("saleOrderId") Long saleOrderId);

    /**
     * OA：该订单是否已有订单维度验收单（一订单一验守卫）
     *
     * @param saleOrderId 来源销售订单ID
     * @return 数量
     */
    int countBySaleOrder(@org.apache.ibatis.annotations.Param("saleOrderId") Long saleOrderId);

    /**
     * D-055 验收维度=客户+日期+配送点：查该维度验收单（首页最新 1 张）
     */
    Acceptance selectByCustomerPointDate(@org.apache.ibatis.annotations.Param("customerId") Long customerId,
                                         @org.apache.ibatis.annotations.Param("customerDeptId") Long customerDeptId,
                                         @org.apache.ibatis.annotations.Param("deliveryDate") java.time.LocalDate deliveryDate);

    /**
     * D-055：该 客户+日期+点 是否已有验收单（防止重复建）
     */
    int countByCustomerPointDate(@org.apache.ibatis.annotations.Param("customerId") Long customerId,
                                 @org.apache.ibatis.annotations.Param("customerDeptId") Long customerDeptId,
                                 @org.apache.ibatis.annotations.Param("deliveryDate") java.time.LocalDate deliveryDate);

    /**
     * 验收维度=客户+配送日期（AC-1，《验收模块订单明细视角重构设计》）：查该维度验收单（最新 1 张）。
     * 只匹配新口径验收单（delivery_order_id is null）；历史送货单验收单 delivery_date 为空天然隔离。
     */
    Acceptance selectByCustomerDate(@org.apache.ibatis.annotations.Param("customerId") Long customerId,
                                    @org.apache.ibatis.annotations.Param("deliveryDate") java.time.LocalDate deliveryDate);

    /**
     * 该 客户+日期 是否已有新口径验收单（AC-3 一客户日一验守卫，防混维度重复建单）
     */
    int countByCustomerDate(@org.apache.ibatis.annotations.Param("customerId") Long customerId,
                            @org.apache.ibatis.annotations.Param("deliveryDate") java.time.LocalDate deliveryDate);

    /**
     * 查询来源订单最近一张已提交验收单的验收日期（月结调整追溯：订单归月用）
     *
     * @param saleOrderId 销售订单ID
     * @return 最近 accept_date，无已提交验收单时为 null
     */
    java.time.LocalDate selectLatestAcceptDateBySaleOrderId(Long saleOrderId);

    /**
     * 批量查询已存在已提交验收单的送货单ID（W0-3.2 列表提醒标色：已验收单不标）
     *
     * @param ids 送货单ID集合
     * @return 已有已提交(status=1)验收单的送货单ID
     */
    List<Long> selectSubmittedDeliveryIds(@org.apache.ibatis.annotations.Param("ids") List<Long> ids);

    /**
     * 新增验收单
     *
     * @param acceptance 验收单
     * @return 结果
     */
    int insertAcceptance(Acceptance acceptance);

    /**
     * 修改验收单
     *
     * @param acceptance 验收单
     * @return 结果
     */
    int updateAcceptance(Acceptance acceptance);

    /**
     * 删除验收单
     *
     * @param id 验收单主键
     * @return 结果
     */
    int deleteAcceptanceById(Long id);

    /**
     * 批量删除验收单
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteAcceptanceByIds(Long[] ids);
}

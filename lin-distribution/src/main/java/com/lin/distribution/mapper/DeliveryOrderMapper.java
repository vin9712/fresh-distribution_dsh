package com.lin.distribution.mapper;
import java.time.LocalDate;

import java.util.Collection;
import java.util.List;

import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.vo.DeliveryBatchPageVO;
import org.apache.ibatis.annotations.Param;

/**
 * 送货单据Mapper接口
 *
 * @author lin
 * @date 2024-12-11
 */
public interface DeliveryOrderMapper {
    /**
     * 查询送货单据
     *
     * @param id 送货单据主键
     * @return 送货单据
     */
    DeliveryOrder selectDeliveryOrderById(Long id);

    /**
     * 批量删除送货单据
     * @param ids
     * @return
     */
    List<DeliveryOrder> selectListByIds(@Param("ids") Collection<Long> ids);

    /**
     * 查该客户当日未作废的有效送货单（S14/T3 统一生成三态分支判定用）
     *
     * <p>含 batch_id 为空的历史单（S14 前生成），用于平滑迁移：
     * 全部未打印的历史单可被重建分支作废后按新模型重建。</p>
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 未作废送货单集合（按 id 升序）
     */
    List<DeliveryOrder> selectActiveByCustomerAndDate(@Param("customerId") Long customerId,
                                                      @Param("deliveryDate") LocalDate deliveryDate);

    /**
     * 查询送货单据列表
     *
     * @param deliveryOrder 送货单据
     * @return 送货单据集合
     */
    List<DeliveryOrder> selectDeliveryOrderList(DeliveryOrder deliveryOrder);

    /**
     * 批次分组聚合分页（D-043，《送货单矩阵总表与批次视图设计》§八）：
     * 按 客户+配送日期 分组，聚合张数/各状态数/合计数量金额/打印形态/提醒最高级。
     *
     * <p>必须后端分组——前端分页会切断同一批次。分组键 = customer_id + delivery_date；
     * 已作废单（status=3）计入 voidedCount 但不计入 docCount 的有效口径；
     * 配送点数为点单去重数（跨点总单 delivery_point_id 为空计 0）。</p>
     *
     * @param deliveryOrder 筛选条件（customerId/deliveryPointId/status/deliveryDate/scopeType 等）
     * @return 批次分组聚合集合
     */
    List<DeliveryBatchPageVO> selectBatchPage(DeliveryOrder deliveryOrder);

    /**
     * 新增送货单据
     *
     * @param deliveryOrder 送货单据
     * @return 结果
     */
    int insertDeliveryOrder(DeliveryOrder deliveryOrder);

    /**
     * 修改送货单据
     *
     * @param deliveryOrder 送货单据
     * @return 结果
     */
    int updateDeliveryOrder(DeliveryOrder deliveryOrder);

    /**
     * 删除送货单据
     *
     * @param id 送货单据主键
     * @return 结果
     */
    int deleteDeliveryOrderById(Long id);

    /**
     * 批量删除送货单据
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteDeliveryOrderByIds(Long[] ids);
}

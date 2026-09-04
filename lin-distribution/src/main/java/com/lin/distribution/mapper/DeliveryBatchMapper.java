package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.DeliveryBatch;
import org.apache.ibatis.annotations.Param;

/**
 * 客户每日配送批次Mapper接口（S14 第一层）
 *
 * @author dsh
 */
public interface DeliveryBatchMapper {

    /**
     * 查询配送批次
     *
     * @param id 批次主键
     * @return 配送批次
     */
    DeliveryBatch selectDeliveryBatchById(Long id);

    /**
     * 查询配送批次列表
     *
     * @param deliveryBatch 查询条件
     * @return 配送批次集合
     */
    List<DeliveryBatch> selectDeliveryBatchList(DeliveryBatch deliveryBatch);

    /**
     * 按客户+配送日期查有效批次（D-055 后仅用于读取历史批次的布局快照）
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 配送批次（无则 null）
     */
    DeliveryBatch selectByCustomerAndDate(@Param("customerId") Long customerId, @Param("deliveryDate") String deliveryDate);

    /**
     * 新增配送批次
     *
     * @param deliveryBatch 配送批次
     * @return 影响行数
     */
    int insertDeliveryBatch(DeliveryBatch deliveryBatch);

    /**
     * 修改配送批次
     *
     * @param deliveryBatch 配送批次
     * @return 影响行数
     */
    int updateDeliveryBatch(DeliveryBatch deliveryBatch);

    /**
     * 【历史只读·D-055 前】客户日总表（S14 §6.1 / D-027/28）：按 source_item 台账聚合 标准品名×配送点 数量（无价格），
     * 返回扁平行由服务层聚合。仅统计有效送货单（status != 3 且 is_deleted = 0）。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 品名×配送点粒度扁平行
     */
    List<com.lin.distribution.vo.DeliveryBatchViewVO.Row> selectBatchViewRowsBySource(@Param("customerId") Long customerId,
                                                                                     @Param("deliveryDate") String deliveryDate);

    /**
     * 【历史只读·D-055 前】客户日总表历史回退：无 source_item 台账的历史单按送货明细行聚合
     * （配送点回退 order.delivery_point_id），返回扁平行由服务层聚合。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 品名×配送点粒度扁平行
     */
    List<com.lin.distribution.vo.DeliveryBatchViewVO.Row> selectBatchViewRowsByDetail(@Param("customerId") Long customerId,
                                                                                      @Param("deliveryDate") String deliveryDate);

    /**
     * 矩阵总表列快照源（D-045）：客户<b>启用</b>配送点（{@code valid=1}），按 {@code code, id} 稳定排序。
     * 停用点不进列（当日有单时由服务层以 adHoc 临时补列，D-053）。
     *
     * @param customerId 客户ID
     * @return 点列（deptId/code/name，adHoc 由服务层置值）
     */
    List<com.lin.distribution.vo.DeliveryMatrixLayout.Column> selectMatrixColumns(@Param("customerId") Long customerId);

    /**
     * 矩阵总表行（D-055 视图化·主口径）：行 = 订单明细按五元组合并（D-024 不同价必拆行），
     * 应送 = SUM(num)；退货行 num 已归 0 自然不计，全组为 0 的行不进矩阵。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 合并行（detailId=组内最小明细ID，仅作展示）
     */
    List<com.lin.distribution.vo.DeliveryMatrixVO.DetailRow> selectMatrixDetailsFromOrder(@Param("customerId") Long customerId,
                                                                                          @Param("deliveryDate") String deliveryDate);

    /**
     * 矩阵总表格（D-055 视图化·主口径）：订单明细按 (五元组, 配送点) 透视应送量。
     * 行身份由服务层按五元组计算，故本查询与 {@link #selectMatrixDetailsFromOrder} 必须回传同一组字段。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 格扁平集（skuId/productName/spec/unit/price/deptId/quantity）
     */
    List<com.lin.distribution.vo.DeliveryMatrixVO.CellRow> selectMatrixCellsFromOrder(@Param("customerId") Long customerId,
                                                                                      @Param("deliveryDate") String deliveryDate);

    /**
     * 客户日总表·配货口径（D-055 视图化·主口径）：订单明细聚合 标准品名×配送点（不拆价不显价 D-027/28）。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 品名×配送点粒度扁平行
     */
    List<com.lin.distribution.vo.DeliveryBatchViewVO.Row> selectBatchViewRowsFromOrder(@Param("customerId") Long customerId,
                                                                                       @Param("deliveryDate") String deliveryDate);

    /**
     * 【历史只读·D-055 前】矩阵总表行：批次内全部有效送货单（原单 + 补充单）的合并明细行，
     * 行身份即 {@code t_delivery_order_detail.id}。仅当订单明细口径无数据时回退使用。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 明细行扁平集（按 doc_kind、单ID、明细ID 升序）
     */
    List<com.lin.distribution.vo.DeliveryMatrixVO.DetailRow> selectMatrixDetails(@Param("customerId") Long customerId,
                                                                                @Param("deliveryDate") String deliveryDate);

    /**
     * 【历史只读·D-055 前】矩阵总表格：按 (送货明细行, 配送点) 透视 source_item 分配量合计。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 格扁平集（detailId/deptId/quantity）
     */
    List<com.lin.distribution.vo.DeliveryMatrixVO.CellRow> selectMatrixCells(@Param("customerId") Long customerId,
                                                                            @Param("deliveryDate") String deliveryDate);
}

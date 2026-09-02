package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.domain.DeliveryBatch;
import com.lin.distribution.vo.DeliveryBatchViewVO;
import com.lin.distribution.vo.DeliveryMatrixLayout;
import com.lin.distribution.vo.DeliveryMatrixVO;

/**
 * 配送批次查询服务（S14 §6.1 / §八，D-027/D-028；矩阵总表 D-044~D-053）
 *
 * @author dsh
 */
public interface DeliveryBatchService {

    /**
     * 客户日总表（内部配货/采购视图）：标准品名 + 总量 + 各配送点小计，无价格、不因价格拆行。
     * 新模型按 t_delivery_source_item 实时聚合；历史单（无台账）回退送货明细行聚合。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期（yyyy-MM-dd）
     * @return 按 标准品名+规格+单位 聚合的视图行（depts 按点名升序）
     */
    List<DeliveryBatchViewVO> selectBatchView(Long customerId, String deliveryDate);

    /**
     * 矩阵总表（D-044/D-047/D-051）：行=送货明细行、列=配送点快照（含空列）、格=分配量透视。
     *
     * <p>纸面不打单价与金额（D-046），同名多行以 {@code (档①)} 标记区分；页面与打印共用本方法。
     * 批次无布局快照时按主数据实时推导（{@code layoutDerived=true}，只读不落库）。</p>
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期（yyyy-MM-dd）
     * @return 矩阵视图（列/行/恒等式校验）
     */
    DeliveryMatrixVO selectMatrix(Long customerId, String deliveryDate);

    /**
     * 刷新批次布局快照（D-045/D-053）：生成/补单/作废重建后调用，写 {@code t_delivery_batch.layout_json}。
     *
     * <p>列与价档均 <b>append-only</b>：已有列顺序、点名快照与档号一律保留，只追加新列/新档；
     * 内容有变化才 {@code layoutVersion+1} 并落库（无变化不写版本，避免每次幂等生成都刷版本）。</p>
     *
     * @param batch    批次（须已落库，含 layoutJson 旧值）
     * @param operator 操作人
     * @return 刷新后的布局快照（batch.layoutJson 同步更新）
     */
    DeliveryMatrixLayout refreshLayout(DeliveryBatch batch, String operator);
}

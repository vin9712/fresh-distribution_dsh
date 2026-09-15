package com.lin.distribution.service;

import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.dto.WithdrawCascadeResultVO;

/**
 * 订单撤回级联服务（W0-2.1，蓝图「撤回级联/共享单据撤回/空关联单据」）
 *
 * <p>撤回已确认订单时，对已生成但尚未执行（送货未打印、采购未入库）的关联单据
 * 执行级联扣除或作废；已打印/已送达送货单与已入库采购单一律拒绝撤回。</p>
 *
 * @author dsh
 */
public interface OrderWithdrawCascadeService {

    /**
     * 撤回前置校验（无副作用）：订单被已打印/已送达送货单或已入库采购单占用时抛异常。
     * 应在状态变更前对全部待撤回订单调用，全部通过后再执行级联。
     *
     * @param saleOrder 待撤回的销售订单（需含单号，用于错误提示）
     */
    void validateOrderWithdrawable(SaleOrder saleOrder);

    /**
     * 撤回级联执行（调用方保证事务上下文）：
     * 送货侧软删该订单的来源分配并重算聚合行，整单无明细则作废（原因=订单撤回）；
     * 采购侧按 order_date 反查当日采购单，按汇总键在批次维度扣除数量并重算金额，
     * 整单无明细则作废（原因=订单撤回）。
     *
     * @param saleOrder 销售订单（需含 id / 单号 / 配送日期）
     * @return 级联执行摘要（作废/扣除的单号清单）
     */
    WithdrawCascadeResultVO cascadeOnOrderWithdraw(SaleOrder saleOrder);
}

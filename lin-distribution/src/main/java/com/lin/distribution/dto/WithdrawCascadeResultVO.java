package com.lin.distribution.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单撤回级联结果（W0-2.1，蓝图「撤回级联/共享单据撤回/空关联单据」）
 *
 * <p>撤回已确认订单时，对未打印送货单与未入库采购单执行扣除/作废的执行摘要，
 * 供服务日志与操作审计使用；不参与前端交互。</p>
 *
 * @author dsh
 */
@Data
@Builder
public class WithdrawCascadeResultVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 自动作废的送货单（空单，原因=订单撤回） */
    @Builder.Default
    private List<String> voidedDeliveryCodes = new ArrayList<>();

    /** 扣除重算后保留的送货单 */
    @Builder.Default
    private List<String> deductedDeliveryCodes = new ArrayList<>();

    /** 自动作废的采购单（空单，原因=订单撤回） */
    @Builder.Default
    private List<String> voidedPurchaseCodes = new ArrayList<>();

    /** 扣除重算后保留的采购单 */
    @Builder.Default
    private List<String> deductedPurchaseCodes = new ArrayList<>();

    public void merge(WithdrawCascadeResultVO other) {
        if (other == null) {
            return;
        }
        voidedDeliveryCodes.addAll(other.getVoidedDeliveryCodes());
        deductedDeliveryCodes.addAll(other.getDeductedDeliveryCodes());
        voidedPurchaseCodes.addAll(other.getVoidedPurchaseCodes());
        deductedPurchaseCodes.addAll(other.getDeductedPurchaseCodes());
    }
}

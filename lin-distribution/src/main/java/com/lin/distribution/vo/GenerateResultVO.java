package com.lin.distribution.vo;

import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.SaleOrder;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 送货单生成结果（S14/T3 统一生成服务返回体，DESIGN.md §5.1）
 *
 * @author dsh
 */
@Data
public class GenerateResultVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 本次使用的配送批次（generateForCustomer 单客户路径有效；generateForDate 多客户批次请读 createdOrders 内的 batchId） */
    private Long batchId;

    /** 本次新建的送货单（含补充单 doc_kind=1） */
    private List<DeliveryOrder> createdOrders = new ArrayList<>();

    /** 幂等跳过明细（无遗漏订单等，供页面提示） */
    private List<String> skippedReasons = new ArrayList<>();

    /** 本轮参与生成的订单集合（作废重建时 = 该客户当日全部已确认订单，D-022） */
    private List<SaleOrder> missedOrders = new ArrayList<>();

    /** 本次随生成一并“草稿→已确认”的订单号（仅 confirmDrafts=true 时非空，供页面提示） */
    private List<String> confirmedOrderCodes = new ArrayList<>();
}

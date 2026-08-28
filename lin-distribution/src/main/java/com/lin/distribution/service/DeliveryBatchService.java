package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.vo.DeliveryBatchViewVO;

/**
 * 配送批次查询服务（S14 §6.1 / §八，D-027/D-028）
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
     * @return 按标准品名聚合的视图行（depts 按点名升序）
     */
    List<DeliveryBatchViewVO> selectBatchView(Long customerId, String deliveryDate);
}

package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.AcceptanceItem;
import com.lin.distribution.dto.AcceptanceUpdateDTO;
import com.lin.distribution.vo.AcceptanceByOrderVO;

/**
 * 验收单Service接口（DESIGN.md §9：一单一验、后端重算实收与损耗）
 *
 * @author dsh
 */
public interface AcceptanceService {
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
     * 按验收单ID查询明细列表
     *
     * @param acceptanceId 验收单主键
     * @return 验收单明细集合
     */
    List<AcceptanceItem> selectItemListByAcceptanceId(Long acceptanceId);

    /**
     * 「去验收」定位（S14 §6.1/§八：订单列表已配送行跳转）。
     *
     * <p>按来源订单反查其所在的有效送货单与验收单：优先走 source_item 有效分配台账，
     * S14 前的历史单回退送货明细行 order_id；已作废单排除；一单分布在多张有效单
     * （补充单场景）时优先返回已建验收单的最新一张，都没有则定位最新单引导创建草稿。</p>
     *
     * @param orderId 来源销售订单ID
     * @return 定位结果（未进入任何有效送货单时仅回显 orderId）
     */
    AcceptanceByOrderVO locateBySaleOrder(Long orderId);

    /**
     * 按送货单生成验收单草稿（一单一验，明细由送货单明细复制）
     *
     * @param deliveryOrderId 送货单ID
     * @return 验收单
     */
    Acceptance createByDeliveryOrder(Long deliveryOrderId);

    /**
     * 录入/修改验收单（仅草稿；实收金额与损耗由后端重算）
     *
     * @param dto 录入请求
     * @return 验收单
     */
    Acceptance updateDraft(AcceptanceUpdateDTO dto);

    /**
     * 提交验收单：状态→已提交，同组订单 → ACCEPTED
     *
     * @param id 验收单主键
     * @return 验收单
     */
    Acceptance submit(Long id);

    /**
     * 撤销验收（S14/T5，DESIGN.md §5.4/Q16/D-014）：已提交→草稿，原因必填，
     * 撤回前主表+明细完整快照落 t_acceptance_revoke_log；来源订单 ACCEPTED→DELIVERED；
     * 任一来源订单 SETTLED → 拒绝。
     *
     * @param id     验收单主键
     * @param reason 撤销原因（必填）
     * @return 验收单
     */
    Acceptance revoke(Long id, String reason);

    /**
     * 批量删除验收单（仅草稿）
     *
     * @param ids 验收单主键集合
     * @return 结果
     */
    int deleteByIds(Long[] ids);
}

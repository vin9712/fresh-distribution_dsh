package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.AcceptanceItem;
import com.lin.distribution.dto.AcceptanceUpdateDTO;

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
     * 批量删除验收单（仅草稿）
     *
     * @param ids 验收单主键集合
     * @return 结果
     */
    int deleteByIds(Long[] ids);
}

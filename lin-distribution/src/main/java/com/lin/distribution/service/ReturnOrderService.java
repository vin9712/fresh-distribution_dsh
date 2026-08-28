package com.lin.distribution.service;

import com.lin.distribution.domain.ReturnItem;
import com.lin.distribution.domain.ReturnOrder;
import com.lin.distribution.dto.ReturnInspectDTO;
import com.lin.distribution.dto.ReturnOrderSaveDTO;

import java.util.List;

/**
 * 退货单Service接口（S14/T6，D-032/D-034/Q31：验收后真实退货走独立退货单，
 * 不撤回历史验收；数量上限=实收-累计已退；单价锁原验收价；settle_scope 提交时快照）
 *
 * @author dsh
 */
public interface ReturnOrderService {

    /**
     * 分页查询退货单列表
     *
     * @param returnOrder 查询条件
     * @return 退货单集合
     */
    List<ReturnOrder> selectReturnOrderList(ReturnOrder returnOrder);

    /**
     * 查询退货单详情
     *
     * @param id 退货单ID
     * @return 退货单
     */
    ReturnOrder selectReturnOrderById(Long id);

    /**
     * 查询退货单明细
     *
     * @param returnId 退货单ID
     * @return 退货明细集合
     */
    List<ReturnItem> selectItemsByReturnId(Long returnId);

    /**
     * 新增退货单草稿（单价后端锁验收价、数量上限校验、金额后端重算）
     *
     * @param dto 保存参数
     * @return 退货单
     */
    ReturnOrder create(ReturnOrderSaveDTO dto);

    /**
     * 修改退货单草稿（明细整体重建重算；仅草稿可改）
     *
     * @param dto 保存参数
     * @return 退货单
     */
    ReturnOrder updateDraft(ReturnOrderSaveDTO dto);

    /**
     * 提交退货单（草稿→已提交质检中；复核数量上限；settle_scope 结算口径快照）
     *
     * @param id 退货单ID
     * @return 退货单
     */
    ReturnOrder submit(Long id);

    /**
     * 质检（已提交→质检完成；逐行记质检结论 1可再售/2不可再售，D-034）
     *
     * @param id 退货单ID
     * @param dto 质检结论
     * @return 退货单
     */
    ReturnOrder inspect(Long id, ReturnInspectDTO dto);

    /**
     * 删除退货单（仅草稿；逻辑删除）
     *
     * @param ids 退货单ID集合
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}

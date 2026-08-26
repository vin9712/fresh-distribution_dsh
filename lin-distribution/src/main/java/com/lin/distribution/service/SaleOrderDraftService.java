package com.lin.distribution.service;

import com.lin.distribution.domain.SaleOrderDraft;

/**
 * 销售订单录入草稿Service接口
 *
 * @author dsh
 */
public interface SaleOrderDraftService {

    /**
     * 按草稿键读取草稿
     *
     * @param draftKey 草稿键
     * @return 草稿（不存在返回 null）
     */
    SaleOrderDraft selectByDraftKey(String draftKey);

    /**
     * 保存（幂等覆盖）草稿
     *
     * @param draftKey 草稿键
     * @param payload  JSON 载荷
     * @return 影响行数
     */
    int saveDraft(String draftKey, String payload);

    /**
     * 删除草稿
     *
     * @param draftKey 草稿键
     * @return 影响行数
     */
    int deleteDraft(String draftKey);
}

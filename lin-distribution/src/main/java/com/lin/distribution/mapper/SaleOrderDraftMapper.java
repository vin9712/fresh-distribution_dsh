package com.lin.distribution.mapper;

import com.lin.distribution.domain.SaleOrderDraft;
import org.apache.ibatis.annotations.Param;

/**
 * 销售订单录入草稿Mapper接口
 *
 * @author dsh
 */
public interface SaleOrderDraftMapper {

    /**
     * 按草稿键查询草稿
     *
     * @param draftKey 草稿键（new:{customerDeptId} / order:{orderId}）
     * @return 草稿（不存在返回 null）
     */
    SaleOrderDraft selectByDraftKey(@Param("draftKey") String draftKey);

    /**
     * 新增或更新草稿（按 draftKey 幂等覆盖，update_time 刷新用于新旧比较）
     *
     * @param draft 草稿
     * @return 影响行数
     */
    int upsert(SaleOrderDraft draft);

    /**
     * 按草稿键删除草稿
     *
     * @param draftKey 草稿键
     * @return 影响行数
     */
    int deleteByDraftKey(@Param("draftKey") String draftKey);
}

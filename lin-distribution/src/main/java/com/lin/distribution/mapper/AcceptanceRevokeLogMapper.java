package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.AcceptanceRevokeLog;

/**
 * 验收撤回审计Mapper接口（S14/Q16/D-014）
 *
 * @author dsh
 */
public interface AcceptanceRevokeLogMapper {

    /**
     * 查询撤回审计记录
     *
     * @param id 主键
     * @return 撤回审计记录
     */
    AcceptanceRevokeLog selectAcceptanceRevokeLogById(Long id);

    /**
     * 查询撤回审计记录列表
     *
     * @param acceptanceRevokeLog 查询条件
     * @return 撤回审计记录集合
     */
    List<AcceptanceRevokeLog> selectAcceptanceRevokeLogList(AcceptanceRevokeLog acceptanceRevokeLog);

    /**
     * 新增撤回审计记录
     *
     * @param acceptanceRevokeLog 撤回审计记录
     * @return 影响行数
     */
    int insertAcceptanceRevokeLog(AcceptanceRevokeLog acceptanceRevokeLog);
}

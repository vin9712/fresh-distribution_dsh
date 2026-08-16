package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.AcceptanceItem;

/**
 * 验收单明细Mapper接口
 *
 * @author dsh
 */
public interface AcceptanceItemMapper {
    /**
     * 查询验收单明细列表
     *
     * @param acceptanceItem 验收单明细
     * @return 验收单明细集合
     */
    List<AcceptanceItem> selectAcceptanceItemList(AcceptanceItem acceptanceItem);

    /**
     * 按验收单ID查询明细列表
     *
     * @param acceptanceId 验收单主键
     * @return 验收单明细集合
     */
    List<AcceptanceItem> selectListByAcceptanceId(Long acceptanceId);

    /**
     * 查询验收单明细
     *
     * @param id 验收单明细主键
     * @return 验收单明细
     */
    AcceptanceItem selectAcceptanceItemById(Long id);

    /**
     * 新增验收单明细
     *
     * @param acceptanceItem 验收单明细
     * @return 结果
     */
    int insertAcceptanceItem(AcceptanceItem acceptanceItem);

    /**
     * 批量新增验收单明细
     *
     * @param items 验收单明细集合
     * @return 结果
     */
    int insertAcceptanceItemBatch(List<AcceptanceItem> items);

    /**
     * 修改验收单明细
     *
     * @param acceptanceItem 验收单明细
     * @return 结果
     */
    int updateAcceptanceItem(AcceptanceItem acceptanceItem);

    /**
     * 按验收单ID删除明细
     *
     * @param acceptanceId 验收单主键
     * @return 结果
     */
    int deleteAcceptanceItemByAcceptanceId(Long acceptanceId);
}

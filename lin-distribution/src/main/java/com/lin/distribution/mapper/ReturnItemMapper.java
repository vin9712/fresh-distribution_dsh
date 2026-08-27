package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.ReturnItem;

/**
 * 退货明细Mapper接口（S14：数量上限=实收-累计已退）
 *
 * @author dsh
 */
public interface ReturnItemMapper {

    /**
     * 查询退货明细
     *
     * @param id 主键
     * @return 退货明细
     */
    ReturnItem selectReturnItemById(Long id);

    /**
     * 查询退货明细列表
     *
     * @param returnItem 查询条件
     * @return 退货明细集合
     */
    List<ReturnItem> selectReturnItemList(ReturnItem returnItem);

    /**
     * 按退货单查明细
     *
     * @param returnId 退货单ID
     * @return 退货明细集合
     */
    List<ReturnItem> selectListByReturnId(Long returnId);

    /**
     * 按验收明细行累计已退数量（退货数量上限校验用）
     *
     * @param acceptanceItemIds 来源验收明细行ID集合
     * @return 已退数量累计（acceptance_item_id → SUM(return_quantity)），按行返回
     */
    List<ReturnItem> sumReturnedByAcceptanceItemIds(List<Long> acceptanceItemIds);

    /**
     * 新增退货明细
     *
     * @param returnItem 退货明细
     * @return 影响行数
     */
    int insertReturnItem(ReturnItem returnItem);

    /**
     * 批量新增退货明细
     *
     * @param items 退货明细集合
     * @return 影响行数
     */
    int batchInsertReturnItem(List<ReturnItem> items);

    /**
     * 修改退货明细
     *
     * @param returnItem 退货明细
     * @return 影响行数
     */
    int updateReturnItem(ReturnItem returnItem);

    /**
     * 删除退货明细
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteReturnItemById(Long id);
}

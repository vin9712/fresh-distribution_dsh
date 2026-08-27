package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.ReturnOrder;

/**
 * 退货单Mapper接口（S14/D-032/D-034）
 *
 * @author dsh
 */
public interface ReturnOrderMapper {

    /**
     * 查询退货单
     *
     * @param id 退货单主键
     * @return 退货单
     */
    ReturnOrder selectReturnOrderById(Long id);

    /**
     * 查询退货单列表
     *
     * @param returnOrder 查询条件
     * @return 退货单集合
     */
    List<ReturnOrder> selectReturnOrderList(ReturnOrder returnOrder);

    /**
     * 新增退货单
     *
     * @param returnOrder 退货单
     * @return 影响行数
     */
    int insertReturnOrder(ReturnOrder returnOrder);

    /**
     * 修改退货单
     *
     * @param returnOrder 退货单
     * @return 影响行数
     */
    int updateReturnOrder(ReturnOrder returnOrder);

    /**
     * 删除退货单
     *
     * @param id 退货单主键
     * @return 影响行数
     */
    int deleteReturnOrderById(Long id);

    /**
     * 批量删除退货单
     *
     * @param ids 主键集合
     * @return 影响行数
     */
    int deleteReturnOrderByIds(Long[] ids);
}

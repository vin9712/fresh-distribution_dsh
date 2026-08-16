package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.Acceptance;

/**
 * 验收单Mapper接口
 *
 * @author dsh
 */
public interface AcceptanceMapper {
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
     * 统计指定送货单已存在的验收单数量（一单一验守卫）
     *
     * @param deliveryOrderId 送货单ID
     * @return 数量
     */
    int countByDeliveryOrderId(Long deliveryOrderId);

    /**
     * 新增验收单
     *
     * @param acceptance 验收单
     * @return 结果
     */
    int insertAcceptance(Acceptance acceptance);

    /**
     * 修改验收单
     *
     * @param acceptance 验收单
     * @return 结果
     */
    int updateAcceptance(Acceptance acceptance);

    /**
     * 删除验收单
     *
     * @param id 验收单主键
     * @return 结果
     */
    int deleteAcceptanceById(Long id);

    /**
     * 批量删除验收单
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteAcceptanceByIds(Long[] ids);
}

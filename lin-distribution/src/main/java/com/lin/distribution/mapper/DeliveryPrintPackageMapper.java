package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.DeliveryPrintPackage;

/**
 * 送货单打印包 Mapper（P2/D-050）
 *
 * @author dsh
 */
public interface DeliveryPrintPackageMapper {

    /**
     * 查打印包（含客户名、包内任务、合计数量金额）
     *
     * @param id 打印包ID
     * @return 打印包（无则 null）
     */
    DeliveryPrintPackage selectPrintPackageById(Long id);

    /**
     * 查批次当日打印包列表（按配送日期/客户）
     *
     * @param customerId   客户ID（可空）
     * @param deliveryDate 配送日期（可空）
     * @return 打印包集合
     */
    List<DeliveryPrintPackage> selectPrintPackageList(DeliveryPrintPackage query);

    /**
     * 新增打印包
     *
     * @param pkg 打印包
     * @return 结果
     */
    int insertPrintPackage(DeliveryPrintPackage pkg);

    /**
     * 更新打印包（状态/计数/预览时间）
     *
     * @param pkg 打印包
     * @return 结果
     */
    int updatePrintPackage(DeliveryPrintPackage pkg);
}

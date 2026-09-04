package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.vo.DeliveryBatchViewVO;
import com.lin.distribution.vo.DeliveryMatrixVO;

/**
 * 配送批次查询服务（S14 §6.1 / §八，D-027/D-028；矩阵总表 D-044~D-053）
 *
 * @author dsh
 */
public interface DeliveryBatchService {

    /**
     * 客户日总表（内部配货/采购视图）：标准品名 + 总量 + 各配送点小计，无价格、不因价格拆行。
     * D-055 主口径按订单明细聚合；无订单数据时回退 D-055 前的送货单台账/送货明细行口径（历史日期只读）。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期（yyyy-MM-dd）
     * @return 按 标准品名+规格+单位 聚合的视图行（depts 按点名升序）
     */
    List<DeliveryBatchViewVO> selectBatchView(Long customerId, String deliveryDate);

    /**
     * D-055 点单视图：按 客户+日期+配送点 返回订单明细行（含变更标记），
     * 供客户日总表页「按配送点查看」口径（订单号+明细+加单/换货/退货 tag）。
     *
     * @param customerId     客户ID
     * @param customerDeptId 配送点ID
     * @param deliveryDate   配送日期
     * @return 订单明细行（sort 升序）
     */
    List<SaleOrderDetail> selectPointView(Long customerId, Long customerDeptId, String deliveryDate);

    /**
     * 矩阵总表（D-044/D-047/D-051 + D-055 视图化）：行=菜品（订单明细五元组合并行）、
     * 列=配送点（含空列）、格=应送量透视。
     *
     * <p>纸面不打单价与金额（D-046），同名多行以备注列 {@code 档①} 区分；页面与打印共用本方法。
     * 批次无布局快照时按启用配送点实时推导（{@code layoutDerived=true}，只读不落库）。</p>
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期（yyyy-MM-dd）
     * @return 矩阵视图（列/行/恒等式校验）
     */
    DeliveryMatrixVO selectMatrix(Long customerId, String deliveryDate);

    /**
     * 打印分界登记（D-055）：一次打印动作记一条。已打印 = 配送后，
     * 后续变更需走带标记的配送后变更（加单/换货/退货）。
     *
     * @param customerId     客户ID
     * @param deliveryDate   配送日期（yyyy-MM-dd）
     * @param customerDeptId 配送点ID（点单打印）；总单打印传 null
     * @param templateId     本次使用的报表模板（可空）
     * @return 登记时间
     */
    java.util.Date markPrinted(Long customerId, String deliveryDate, Long customerDeptId, Long templateId);

    /**
     * 该 客户+日期(+配送点) 是否已打印（D-055 打印分界判定）
     *
     * @param customerId     客户ID
     * @param deliveryDate   配送日期（yyyy-MM-dd）
     * @param customerDeptId 配送点ID；传 null 则判总单维度
     * @return true=已有打印记录
     */
    boolean isPrinted(Long customerId, String deliveryDate, Long customerDeptId);
}

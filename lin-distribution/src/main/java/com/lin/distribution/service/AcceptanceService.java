package com.lin.distribution.service;

import java.time.LocalDate;
import java.util.List;

import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.AcceptanceItem;
import com.lin.distribution.dto.AcceptanceUpdateDTO;
import com.lin.distribution.vo.AcceptanceByOrderVO;

/**
 * 验收单Service接口（DESIGN.md §9：一单一验、后端重算实收与损耗）
 *
 * @author dsh
 */
public interface AcceptanceService {
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
     * 按验收单ID查询明细列表
     *
     * @param acceptanceId 验收单主键
     * @return 验收单明细集合
     */
    List<AcceptanceItem> selectItemListByAcceptanceId(Long acceptanceId);

    /**
     * 「去验收」定位（S14 §6.1/§八 + AC-5 订单视角优先）。
     *
     * <p>订单视角（新流程，status=1 主场景）：按订单行取 客户+配送日期，
     * 查该客户日是否已有新口径验收单 → 返回 customerId/deliveryDate + 命中的验收单信息，
     * 无单时前端带 create 参数引导一键建草稿；</p>
     * <p>历史回退（status=2 已配送的历史单）：原样保留送货单反查链路——
     * source_item 有效分配台账 → 历史单送货明细行 order_id → 排除作废 →
     * 补充单场景优先取已建验收单的最新一张。</p>
     *
     * @param orderId 来源销售订单ID
     * @return 定位结果（仅回显 orderId = 既无客户日验收单也未进历史送货单）
     */
    AcceptanceByOrderVO locateBySaleOrder(Long orderId);

    /**
     * 按送货单生成验收单草稿（一单一验，明细由送货单明细复制）
     *
     * @param deliveryOrderId 送货单ID
     * @return 验收单
     * @deprecated 历史单专用（AC-4，《验收模块订单明细视角重构设计》）：新流程验收维度=客户+配送日期，
     *             唯一建单入口为 {@link #createByCustomerDate(Long, LocalDate)}；
     *             本方法仅为历史 status=2 订单补建验收保留，新调用禁止。
     */
    @Deprecated
    Acceptance createByDeliveryOrder(Long deliveryOrderId);

    /**
     * D-055 按「客户+日期+配送点」生成验收单草稿：应送行=订单明细（含加单/换货/退货标记），
     * 一维一验；默认实收=应送（补充单行取订单明细 actual_num）。
     *
     * @param customerId     客户ID
     * @param customerDeptId 配送点ID
     * @param deliveryDate   配送日期
     * @return 验收单
     * @deprecated 已升级为客户日维度（AC-1），由 {@link #createByCustomerDate(Long, LocalDate)} 替代
     */
    @Deprecated
    Acceptance createByCustomerPoint(Long customerId, Long customerDeptId, LocalDate deliveryDate);

    /**
     * 按「客户+配送日期」生成验收单草稿（AC-1/AC-2，订单明细视角）：
     * 一客户日一张，应送行=该客户当日全部订单明细行（跨配送点平铺，行带配送点/订单号/变更标记），
     * 默认实收=应送（加单行取订单明细 actual_num 镜像，退货行应送实收归 0）；
     * 一客户日一验守卫（AC-3：含按点历史遗留，防混维度重复建单）。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 验收单
     */
    Acceptance createByCustomerDate(Long customerId, LocalDate deliveryDate);

    /**
     * 录入/修改验收单（仅草稿；实收金额与损耗由后端重算）
     *
     * @param dto 录入请求
     * @return 验收单
     */
    Acceptance updateDraft(AcceptanceUpdateDTO dto);

    /**
     * 提交验收单：状态→已提交，同组订单 → ACCEPTED
     *
     * @param id 验收单主键
     * @return 验收单
     */
    Acceptance submit(Long id);

    /**
     * 撤销验收（S14/T5，DESIGN.md §5.4/Q16/D-014）：已提交→草稿，原因必填，
     * 撤回前主表+明细完整快照落 t_acceptance_revoke_log；来源订单 ACCEPTED→DELIVERED；
     * 任一来源订单 SETTLED → 拒绝。
     *
     * @param id     验收单主键
     * @param reason 撤销原因（必填）
     * @return 验收单
     */
    Acceptance revoke(Long id, String reason);

    /**
     * 批量删除验收单（仅草稿）
     *
     * @param ids 验收单主键集合
     * @return 结果
     */
    int deleteByIds(Long[] ids);
}

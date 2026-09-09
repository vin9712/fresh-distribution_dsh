package com.lin.distribution.service;

import java.time.LocalDate;
import java.util.List;

import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.AcceptanceItem;
import com.lin.distribution.dto.AcceptanceQuickAcceptDTO;
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
     * 「去验收」定位（S14 §6.1/§八 + AC-5 + OA 订单维度优先）。
     *
     * <p>① OA 订单维度优先（《订单页一键验收链路设计》）：sale_order_id 反查订单维度验收单，
     * 命中返回 orderView=true + 验收单信息；</p>
     * <p>② 客户日维度过渡兼容（AC-5）：订单行取 客户+配送日期，查该客户日是否已有客户日验收单，
     * 命中同标 orderView=true（旧维度单只读打开，新单据不再创建）；</p>
     * <p>③ 新流程订单（status=1 已确认）无单可验：返回 orderView=true + customerId/deliveryDate，
     * 前端引导一键建草稿；</p>
     * <p>④ 历史回退（status=2 已配送的历史单）：原样保留送货单反查链路——
     * source_item 有效分配台账 → 历史单送货明细行 order_id → 排除作废 →
     * 补充单场景优先取已建验收单的最新一张。</p>
     *
     * @param orderId 来源销售订单ID
     * @return 定位结果（仅回显 orderId = 无任何可验收单）
     */
    AcceptanceByOrderVO locateBySaleOrder(Long orderId);

    /**
     * OA：按订单生成（或同步）验收草稿——《订单页一键验收链路设计》§4.3：
     * 一订单一验：无单则建草稿（应送行=该订单全部有效明细，含 D-055 标记行）；
     * 已有草稿则同步缺失行（验收中途做了加单/换货/退货），幂等返回草稿；
     * 已提交则拒绝（提示先撤销）。
     *
     * @param orderId 来源销售订单ID
     * @return 验收草稿
     */
    Acceptance createByOrder(Long orderId);

    /**
     * OA：订单一键验收——《订单页一键验收链路设计》§4.5：
     * 建单（如无）→ 同步缺失行 → 应用实收覆盖（可选，缺省=全部实收等于下单数量）→
     * 重算金额 → 提交（订单 → 已验收，回写 actual_* 镜像），事务内原子完成。
     *
     * @param dto 一键验收请求（orderId 必填）
     * @return 验收单（已提交）
     */
    Acceptance quickAccept(AcceptanceQuickAcceptDTO dto);

    /**
     * OA：同步订单维度验收草稿的缺失行（验收中途做了加单/换货/退货后调用）：
     * 只补插订单新增的明细行、重置被标退货的行（应送实收归0），不删旧行、不覆盖已录实收。
     *
     * @param acceptanceId 验收单ID（须为订单维度草稿）
     * @return 同步后的验收单（重算 totalAmount）
     */
    Acceptance syncMissingItems(Long acceptanceId);

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

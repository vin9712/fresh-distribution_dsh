package com.lin.distribution.service;

import com.lin.distribution.dto.PrintTicketPayload;

/**
 * 打印票据服务（W0-4.1：打印鉴权改短时一次性票据，废除 URL 携带 JWT）
 *
 * <p>票据模型（Redis）：
 * <ul>
 *   <li>未使用：{@code print_ticket:unused:<ticket>}，TTL 300 秒，过期自动作废；</li>
 *   <li>已兑换：首次校验消费未用票据，落 {@code print_ticket:used:<ticket>}，宽限 600 秒——
 *       因 JimuReport 打开一个报表页会发多次请求（视图渲染、服务端 API 数据集回调、导出打印）
 *       且均复用 URL 上同一票据，宽限窗口仅覆盖"本次报表会话"，票据本身不可二次签发重放；</li>
 *   <li>绑定：数据接口取数时票据的 deliveryOrderId 必须与请求一致。</li>
 * </ul>
 *
 * @author dsh
 */
public interface PrintTicketService {

    /** 票据前缀：JimuReport 桥接据此区分票据与 JWT */
    String TICKET_PREFIX = "ptk_";

    /**
     * 签发短时一次性打印票据（记录当前登录用户为操作者）
     *
     * @param deliveryOrderId 绑定送货单（可空：模板设计器等无单据场景）
     * @param templateId      绑定报表模板（可空）
     * @return ptk_ 前缀票据，TTL 300 秒
     */
    String issue(Long deliveryOrderId, Long templateId);

    /**
     * 签发票据（指定用途，PR-D5）
     *
     * @param deliveryOrderId 绑定送货单（可空）
     * @param templateId      绑定报表模板（可空）
     * @param scope           用途：{@link PrintTicketPayload#SCOPE_PRINT} / {@link PrintTicketPayload#SCOPE_PREVIEW}；
     *                        空或非法值归一化为真实打印
     * @return ptk_ 前缀票据，TTL 300 秒
     */
    String issue(Long deliveryOrderId, Long templateId, String scope);

    /**
     * 签发打印票据（D-055 视图化：无送货单ID 的 客户+日期(+点) 打印主体）
     *
     * @param bizKey     打印主体键（如 {@code matrix:10:2026-09-03} / {@code point:10:6:2026-09-03}）
     * @param templateId 绑定报表模板（可空）
     * @return ptk_ 前缀票据，TTL 300 秒
     */
    String issueByBizKey(String bizKey, Long templateId);

    /**
     * 签发打印主体票据（指定用途，PR-D5）：预览票据不登记打印分界
     *
     * @param bizKey     打印主体键
     * @param templateId 绑定报表模板（可空）
     * @param scope      用途：print / preview
     * @return ptk_ 前缀票据，TTL 300 秒
     */
    String issueByBizKey(String bizKey, Long templateId, String scope);

    /**
     * 兑换票据（JimuReport 桥接调用）：首次调用消费未用票据，宽限窗口内复用返回同一用户
     *
     * @param ticket ptk_ 前缀票据
     * @return 绑定的登录用户名；票据无效/过期返回 null
     */
    String redeem(String ticket);

    /**
     * 数据接口取数校验（非消费式）：票据有效（未用或宽限期内）且与请求送货单绑定一致
     *
     * @param ticket          票据
     * @param deliveryOrderId 请求取数的送货单
     * @return true 允许取数
     */
    boolean validateDataAccess(String ticket, Long deliveryOrderId);

    /**
     * 数据接口取数校验（D-055 视图化打印主体）：票据有效且绑定的 bizKey 与请求主体一致
     *
     * @param ticket 票据
     * @param bizKey 请求的打印主体键
     * @return true 允许取数
     */
    boolean validateDataAccessByBizKey(String ticket, String bizKey);

    /**
     * 打印回执领取（PT-3，《客户日报表打印优化设计》§3.3）：
     * JimuReport 页面在真实打印动作后回传票据，取回负载以定位打印主体（登记打印分界）。
     * 未用票据一次性领取转宽限（页面可能未发数据集回调就直接打印）；
     * 已用票据宽限期内复用；无效/过期返回 null（回执端永远 2xx，不打断打印）。
     *
     * @param ticket ptk_ 前缀票据
     * @return 票据负载；无效/过期返回 null
     */
    PrintTicketPayload consumeForReceipt(String ticket);
}

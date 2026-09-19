package com.lin.distribution.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 打印票据负载（W0-4.1 短时一次性打印票据）
 * 存于 Redis，替代 URL 携带长期 JWT：
 * - username：签发时登录用户（JimuReport 桥接兑换后以此识别操作者）；
 * - deliveryOrderId：绑定的送货单（D-055 前的旧单证打印；数据接口取数时强校验，防串单越权；设计器票据为空）；
 * - bizKey：D-055 视图化后的打印主体键（无送货单ID 场景，如 {@code matrix:10:2026-09-03} / {@code point:10:6:2026-09-03}）；
 * - templateId：绑定的报表模板（可空）；
 * - scope：票据用途（print=真实打印，可登记打印分界；preview=模板预览，永不登记分界，PR-D5）。
 *
 * @author dsh
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrintTicketPayload {

    /** 票据用途：真实打印（默认；真实打印后可登记打印分界） */
    public static final String SCOPE_PRINT = "print";
    /** 票据用途：模板预览（绝不登记打印分界，避免预览污染 D-055 分界） */
    public static final String SCOPE_PREVIEW = "preview";

    private String username;
    private Long deliveryOrderId;
    private String bizKey;
    private Long templateId;
    private String scope;

    /** 兼容旧四参构造（无 scope，默认真实打印） */
    public PrintTicketPayload(String username, Long deliveryOrderId, String bizKey, Long templateId) {
        this(username, deliveryOrderId, bizKey, templateId, SCOPE_PRINT);
    }

    /** 兼容旧三参构造（无 bizKey 的送货单绑定） */
    public PrintTicketPayload(String username, Long deliveryOrderId, Long templateId) {
        this(username, deliveryOrderId, null, templateId, SCOPE_PRINT);
    }
}

package com.lin.distribution.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 打印票据负载（W0-4.1 短时一次性打印票据）
 * 存于 Redis，替代 URL 携带长期 JWT：
 * - username：签发时登录用户（JimuReport 桥接兑换后以此识别操作者）；
 * - deliveryOrderId：绑定的送货单（数据接口取数时强校验，防串单越权；设计器票据为空）；
 * - templateId：绑定的报表模板（可空）。
 *
 * @author dsh
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrintTicketPayload {
    private String username;
    private Long deliveryOrderId;
    private Long templateId;
}

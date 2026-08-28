package com.lin.jmreport;

import com.lin.common.core.domain.model.LoginUser;
import com.lin.distribution.service.PrintTicketService;
import com.lin.framework.web.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.jmreport.api.JmReportTokenServiceI;
import org.springframework.stereotype.Component;

/**
 * JimuReport 鉴权桥接：
 * - SecurityConfig 放行 /jmreport/**（由本服务校验）；
 * - 两种凭据（W0-4.1）：
 *   1. 短时一次性打印票据（{@code ptk_} 前缀，前端签发后以 ?ticket → token 参数传入，
 *      兼作预览/导出/打印同一次报表会话的复用，宽限窗口由票据服务控制）——
 *      替代原先 URL 携带长期 JWT 的方式；
 *   2. 其余仍走 JWT（签名解析 + Redis 会话查询，支持吊销），兼容历史链路。
 *
 * @author dsh
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JimuReportTokenServiceImpl implements JmReportTokenServiceI {

    private final TokenService tokenService;

    private final PrintTicketService printTicketService;

    @Override
    public String getUsername(String token) {
        try {
            if (token != null && token.startsWith(PrintTicketService.TICKET_PREFIX)) {
                // W0-4.1：短时一次性打印票据，不再解析 JWT
                return printTicketService.redeem(token);
            }
            LoginUser loginUser = tokenService.getLoginUserByToken(token);
            return loginUser == null ? null : loginUser.getUsername();
        } catch (Exception e) {
            log.error("[jimureport-auth] getUsername failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Boolean verifyToken(String token) {
        return StringUtils.isNotBlank(getUsername(token));
    }
}

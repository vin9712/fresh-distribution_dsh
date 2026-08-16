package com.lin.jmreport;

import com.lin.common.core.domain.model.LoginUser;
import com.lin.framework.web.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.jmreport.api.JmReportTokenServiceI;
import org.springframework.stereotype.Component;

/**
 * JimuReport 鉴权桥接：
 * - SecurityConfig 放行 /jmreport/**（由本服务校验）；
 * - JimuReport 请求携带 token 参数（前端 iframe/请求 URL 追加 ?token=<JWT>）；
 * - 校验逻辑：JWT 签名解析 + Redis 会话查询（支持吊销），与 RuoYi 主链路一致。
 *
 * @author dsh
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JimuReportTokenServiceImpl implements JmReportTokenServiceI {

    private final TokenService tokenService;

    @Override
    public String getUsername(String token) {
        try {
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

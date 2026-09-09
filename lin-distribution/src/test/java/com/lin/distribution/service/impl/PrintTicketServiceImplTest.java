package com.lin.distribution.service.impl;

import com.lin.common.core.domain.entity.SysUser;
import com.lin.common.core.domain.model.LoginUser;
import com.lin.common.core.redis.RedisCache;
import com.lin.distribution.dto.PrintTicketPayload;
import com.lin.distribution.service.PrintTicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打印票据服务测试（W0-4.1：打印鉴权改短时一次性票据，废除 URL 携带 JWT）
 * 覆盖：签发绑定、一次性兑换、宽限复用、过期失效、数据接口绑定校验。
 */
@ExtendWith(MockitoExtension.class)
class PrintTicketServiceImplTest {

    @Mock
    private RedisCache redisCache;

    @InjectMocks
    private PrintTicketServiceImpl printTicketService;

    @BeforeEach
    void setUp() {
        LoginUser loginUser = new LoginUser();
        SysUser user = new SysUser();
        user.setUserName("clerk");
        loginUser.setUser(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private PrintTicketPayload payload(Long deliveryOrderId) {
        return new PrintTicketPayload("clerk", deliveryOrderId, 2099000000000000001L);
    }

    private PrintTicketPayload bizPayload(String bizKey) {
        return new PrintTicketPayload("clerk", null, bizKey, 2599000000000000001L);
    }

    @Test
    void 签发绑定打印主体bizKey_视图化无送货单ID() {
        String ticket = printTicketService.issueByBizKey("matrix:10:2026-09-03", 2599000000000000001L);

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(redisCache).setCacheObject(eq(PrintTicketServiceImpl.UNUSED_KEY + ticket),
                valueCaptor.capture(), eq(PrintTicketServiceImpl.UNUSED_TTL_SECONDS), eq(TimeUnit.SECONDS));
        PrintTicketPayload stored = (PrintTicketPayload) valueCaptor.getValue();
        assertEquals("matrix:10:2026-09-03", stored.getBizKey());
        assertNull(stored.getDeliveryOrderId(), "视图化打印主体无送货单ID");
    }

    @Test
    void 数据接口校验_票据与打印主体一致才放行() {
        String ticket = PrintTicketService.TICKET_PREFIX + "biz1";
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + ticket))
                .thenReturn(bizPayload("point:10:6:2026-09-03"));
        assertTrue(printTicketService.validateDataAccessByBizKey(ticket, "point:10:6:2026-09-03"));

        // 跨客户/跨日期取数：主体不一致拒绝
        String ticket2 = PrintTicketService.TICKET_PREFIX + "biz2";
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + ticket2))
                .thenReturn(bizPayload("point:10:6:2026-09-03"));
        assertFalse(printTicketService.validateDataAccessByBizKey(ticket2, "point:11:6:2026-09-03"));

        // 缺票据 / 缺主体一律拒绝
        assertFalse(printTicketService.validateDataAccessByBizKey(null, "matrix:10:2026-09-03"));
        assertFalse(printTicketService.validateDataAccessByBizKey(ticket, "  "));
    }

    @Test
    void 签发绑定当前用户与送货单且写入未用票据() {
        String ticket = printTicketService.issue(500L, 2099000000000000001L);

        assertTrue(ticket.startsWith(PrintTicketService.TICKET_PREFIX));
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(redisCache).setCacheObject(eq(PrintTicketServiceImpl.UNUSED_KEY + ticket),
                valueCaptor.capture(), eq(PrintTicketServiceImpl.UNUSED_TTL_SECONDS), eq(TimeUnit.SECONDS));
        PrintTicketPayload stored = (PrintTicketPayload) valueCaptor.getValue();
        assertEquals("clerk", stored.getUsername());
        assertEquals(500L, stored.getDeliveryOrderId());
        assertEquals(2099000000000000001L, stored.getTemplateId());
    }

    @Test
    void 首次兑换消费未用票据并转入宽限() {
        String ticket = PrintTicketService.TICKET_PREFIX + "abc";
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + ticket)).thenReturn(payload(500L));

        String username = printTicketService.redeem(ticket);

        assertEquals("clerk", username);
        // 一次性：未用票据被删除
        verify(redisCache).deleteObject(PrintTicketServiceImpl.UNUSED_KEY + ticket);
        // 转入宽限期供本次报表会话复用
        verify(redisCache).setCacheObject(eq(PrintTicketServiceImpl.USED_KEY + ticket), any(),
                eq(PrintTicketServiceImpl.USED_GRACE_SECONDS), eq(TimeUnit.SECONDS));
    }

    @Test
    void 宽限期内同票据复用返回同一用户() {
        String ticket = PrintTicketService.TICKET_PREFIX + "abc";
        // 未用票据已消费（第二次领取返回 null），宽限 key 命中
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + ticket)).thenReturn(null);
        when(redisCache.getCacheObject(PrintTicketServiceImpl.USED_KEY + ticket)).thenReturn(payload(500L));

        String username = printTicketService.redeem(ticket);

        assertEquals("clerk", username);
        // 宽限复用不得续签未用票据
        verify(redisCache, never()).setCacheObject(anyString(), any(), anyInt(), any(TimeUnit.class));
    }

    @Test
    void 票据过期或非票据token兑换返回空() {
        String ticket = PrintTicketService.TICKET_PREFIX + "expired";
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + ticket)).thenReturn(null);
        when(redisCache.getCacheObject(PrintTicketServiceImpl.USED_KEY + ticket)).thenReturn(null);
        assertNull(printTicketService.redeem(ticket));
        // JWT 形态不进入票据兑换
        assertNull(printTicketService.redeem("eyJhbGciOiJIUzUxMiJ9.xxx.yyy"));
        assertNull(printTicketService.redeem(null));
    }

    @Test
    void 数据接口校验_票据与送货单绑定一致才放行() {
        String ticket = PrintTicketService.TICKET_PREFIX + "abc";
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + ticket)).thenReturn(payload(500L));
        assertTrue(printTicketService.validateDataAccess(ticket, 500L));

        // 换单取数：绑定不一致拒绝（防串单越权）
        String ticket2 = PrintTicketService.TICKET_PREFIX + "def";
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + ticket2)).thenReturn(payload(500L));
        assertFalse(printTicketService.validateDataAccess(ticket2, 501L));

        // 无绑定票据（设计器场景）不允许拉业务数据
        String ticket3 = PrintTicketService.TICKET_PREFIX + "ghi";
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + ticket3))
                .thenReturn(new PrintTicketPayload("clerk", null, 1L));
        assertFalse(printTicketService.validateDataAccess(ticket3, 500L));

        // 缺票据一律拒绝
        assertFalse(printTicketService.validateDataAccess(null, 500L));
        assertFalse(printTicketService.validateDataAccess("", 500L));
    }

    @Test
    void 数据接口校验走宽限语义不阻断后续数据集回调() {
        String ticket = PrintTicketService.TICKET_PREFIX + "abc";
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + ticket)).thenReturn(payload(500L));
        assertTrue(printTicketService.validateDataAccess(ticket, 500L));
        // hd/dd 两次数据集回调均可在宽限期内通过
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + ticket)).thenReturn(null);
        when(redisCache.getCacheObject(PrintTicketServiceImpl.USED_KEY + ticket)).thenReturn(payload(500L));
        assertTrue(printTicketService.validateDataAccess(ticket, 500L));
    }

    @Test
    void 两次签发票据互不相同() {
        String t1 = printTicketService.issue(500L, null);
        String t2 = printTicketService.issue(500L, null);
        assertNotEquals(t1, t2);
    }

    // ==================== 打印回执领取（PT-3，《客户日报表打印优化设计》§3.3） ====================

    @Test
    void 回执领取_未用票据一次性领取并转宽限() {
        String ticket = PrintTicketService.TICKET_PREFIX + "rcpt1";
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + ticket))
                .thenReturn(bizPayload("point:10:6:2026-09-03"));

        PrintTicketPayload got = printTicketService.consumeForReceipt(ticket);

        assertNotNull(got);
        assertEquals("point:10:6:2026-09-03", got.getBizKey());
        // 一次性领取：未用票据被删除并转入宽限（页面可能未发数据集回调就直接打印）
        verify(redisCache).deleteObject(PrintTicketServiceImpl.UNUSED_KEY + ticket);
        verify(redisCache).setCacheObject(eq(PrintTicketServiceImpl.USED_KEY + ticket), any(),
                eq(PrintTicketServiceImpl.USED_GRACE_SECONDS), eq(TimeUnit.SECONDS));
    }

    @Test
    void 回执领取_宽限期内复用且无效票据返回空() {
        // 宽限期内复用
        String ticket = PrintTicketService.TICKET_PREFIX + "rcpt2";
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + ticket)).thenReturn(null);
        when(redisCache.getCacheObject(PrintTicketServiceImpl.USED_KEY + ticket))
                .thenReturn(bizPayload("matrix:10:2026-09-03"));
        PrintTicketPayload got = printTicketService.consumeForReceipt(ticket);
        assertNotNull(got);
        assertEquals("matrix:10:2026-09-03", got.getBizKey());

        // 无效/过期票据返回 null（回执端永远 2xx）
        String bad = PrintTicketService.TICKET_PREFIX + "bad";
        when(redisCache.getCacheObject(PrintTicketServiceImpl.UNUSED_KEY + bad)).thenReturn(null);
        when(redisCache.getCacheObject(PrintTicketServiceImpl.USED_KEY + bad)).thenReturn(null);
        assertNull(printTicketService.consumeForReceipt(bad));
        assertNull(printTicketService.consumeForReceipt(null));
    }
}

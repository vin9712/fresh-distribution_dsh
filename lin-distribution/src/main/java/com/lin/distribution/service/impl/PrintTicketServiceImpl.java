package com.lin.distribution.service.impl;

import com.lin.common.core.redis.RedisCache;
import com.lin.common.utils.SecurityUtils;
import com.lin.common.utils.uuid.IdUtils;
import com.lin.distribution.dto.PrintTicketPayload;
import com.lin.distribution.service.PrintTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 打印票据服务实现（W0-4.1）
 * 详见 {@link PrintTicketService} 契约说明。
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintTicketServiceImpl implements PrintTicketService {

    static final String UNUSED_KEY = "print_ticket:unused:";
    static final String USED_KEY = "print_ticket:used:";
    /** 签发后未兑换有效期（秒）：足够"确认打印 → 打开报表页"一次往返 */
    static final int UNUSED_TTL_SECONDS = 300;
    /** 兑换后宽限期（秒）：覆盖单次报表会话（渲染 + 数据集回调 + 导出打印） */
    static final int USED_GRACE_SECONDS = 600;

    private final RedisCache redisCache;

    @Override
    public String issue(Long deliveryOrderId, Long templateId) {
        return issue(deliveryOrderId, templateId, null);
    }

    @Override
    public String issueByBizKey(String bizKey, Long templateId) {
        return issue(null, templateId, bizKey);
    }

    private String issue(Long deliveryOrderId, Long templateId, String bizKey) {
        String username = SecurityUtils.getUsername();
        String ticket = TICKET_PREFIX + IdUtils.fastSimpleUUID();
        PrintTicketPayload payload = new PrintTicketPayload(username, deliveryOrderId, bizKey, templateId);
        redisCache.setCacheObject(UNUSED_KEY + ticket, payload, UNUSED_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("[print-ticket] issued user={} deliveryOrderId={} bizKey={} templateId={}",
                username, deliveryOrderId, bizKey, templateId);
        return ticket;
    }

    @Override
    public String redeem(String ticket) {
        PrintTicketPayload payload = claimUnused(ticket);
        if (payload != null) {
            // 首次兑换：未用票据一次性消费，转入宽限期供本次报表会话复用
            redisCache.setCacheObject(USED_KEY + ticket, payload, USED_GRACE_SECONDS, TimeUnit.SECONDS);
            return payload.getUsername();
        }
        // 宽限期内复用（同一次报表会话的后续请求）
        payload = redisCache.getCacheObject(USED_KEY + ticket);
        return payload == null ? null : payload.getUsername();
    }

    @Override
    public boolean validateDataAccess(String ticket, Long deliveryOrderId) {
        if (deliveryOrderId == null) {
            return false;
        }
        PrintTicketPayload payload = redeemForDataAccess(ticket);
        // 绑定校验：票据必须绑定该送货单，防跨单越权取数；无绑定票据（设计器场景）不允许取业务数据
        return payload != null && deliveryOrderId.equals(payload.getDeliveryOrderId());
    }

    @Override
    public boolean validateDataAccessByBizKey(String ticket, String bizKey) {
        if (StringUtils.isBlank(bizKey)) {
            return false;
        }
        PrintTicketPayload payload = redeemForDataAccess(ticket);
        return payload != null && bizKey.equals(payload.getBizKey());
    }

    /**
     * 打印回执领取（PT-3）：语义与数据取数兑换同构（非消费式占位→宽限复用），
     * 但未用票据也转入宽限——报表页可能未发数据集回调就直接打印。
     */
    @Override
    public PrintTicketPayload consumeForReceipt(String ticket) {
        if (StringUtils.isBlank(ticket)) {
            return null;
        }
        PrintTicketPayload payload = claimUnused(ticket);
        if (payload != null) {
            redisCache.setCacheObject(USED_KEY + ticket, payload, USED_GRACE_SECONDS, TimeUnit.SECONDS);
            return payload;
        }
        return redisCache.getCacheObject(USED_KEY + ticket);
    }

    /**
     * 取数校验用的票据兑换（非消费式）：首次命中未用票据时转入宽限期，
     * 同一报表会话（渲染 + 多个数据集回调 + 导出）可复用。
     */
    private PrintTicketPayload redeemForDataAccess(String ticket) {
        if (StringUtils.isBlank(ticket)) {
            return null;
        }
        PrintTicketPayload payload = claimUnused(ticket);
        if (payload == null) {
            return redisCache.getCacheObject(USED_KEY + ticket);
        }
        // 数据接口取数属于报表会话的一部分：保持宽限语义，不阻断后续数据集回调
        redisCache.setCacheObject(USED_KEY + ticket, payload, USED_GRACE_SECONDS, TimeUnit.SECONDS);
        return payload;
    }

    /**
     * 一次性领取未使用票据（存在则删除并返回负载；不存在返回 null）
     */
    private PrintTicketPayload claimUnused(String ticket) {
        if (StringUtils.isBlank(ticket) || !ticket.startsWith(TICKET_PREFIX)) {
            return null;
        }
        PrintTicketPayload payload = redisCache.getCacheObject(UNUSED_KEY + ticket);
        if (payload != null) {
            redisCache.deleteObject(UNUSED_KEY + ticket);
        }
        return payload;
    }
}

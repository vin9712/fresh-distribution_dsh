package com.lin.distribution.service.impl;

import com.lin.common.utils.SecurityUtils;
import com.lin.common.utils.StringUtils;
import com.lin.distribution.domain.SaleOrderDraft;
import com.lin.distribution.mapper.SaleOrderDraftMapper;
import com.lin.distribution.service.SaleOrderDraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 销售订单录入草稿Service业务层处理
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaleOrderDraftServiceImpl implements SaleOrderDraftService {
    private final SaleOrderDraftMapper saleOrderDraftMapper;

    @Override
    public SaleOrderDraft selectByDraftKey(String draftKey) {
        if (StringUtils.isBlank(draftKey)) {
            return null;
        }
        return saleOrderDraftMapper.selectByDraftKey(draftKey);
    }

    @Override
    public int saveDraft(String draftKey, String payload) {
        if (StringUtils.isBlank(draftKey) || StringUtils.isBlank(payload)) {
            return 0;
        }
        Long userId = currentUserId();
        SaleOrderDraft draft = SaleOrderDraft.builder()
                .draftKey(draftKey)
                .userId(userId)
                .payload(payload)
                .build();
        return saleOrderDraftMapper.upsert(draft);
    }

    @Override
    public int deleteDraft(String draftKey) {
        if (StringUtils.isBlank(draftKey)) {
            return 0;
        }
        return saleOrderDraftMapper.deleteByDraftKey(draftKey);
    }

    /**
     * 当前登录用户；定时任务/匿名上下文返回 0
     */
    private Long currentUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return 0L;
        }
    }
}

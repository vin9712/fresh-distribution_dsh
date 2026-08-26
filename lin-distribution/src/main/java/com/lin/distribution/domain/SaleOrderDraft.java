package com.lin.distribution.domain;

import com.lin.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * 销售订单录入草稿对象 t_sale_order_draft
 * 录单页草稿自动保存的后端存储（后端为主、localStorage 兜底断网场景）
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SaleOrderDraft extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 草稿键（new:{customerDeptId} / order:{orderId}）
     */
    private String draftKey;

    /**
     * 用户ID（0 = 未关联用户）
     */
    private Long userId;

    /**
     * 草稿载荷（JSON：表头 + 明细 + savedAt）
     */
    private String payload;}

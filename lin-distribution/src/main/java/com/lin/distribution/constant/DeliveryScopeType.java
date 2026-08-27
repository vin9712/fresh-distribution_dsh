package com.lin.distribution.constant;

import org.apache.commons.lang3.StringUtils;

/**
 * 组单策略范围（S14 三层模型第二层取值，客户级配置 t_customer.doc_scope_type，
 * 生成瞬间快照进 t_delivery_batch.scope_type / t_delivery_order.scope_type；D-015 配送点不覆盖）
 *
 * @author dsh
 */
public final class DeliveryScopeType {
    /** A类：跨配送点客户日总单（t_delivery_order.delivery_point_id = NULL，配送点维度仅留在 source_item） */
    public static final String CUSTOMER_DATE = "CUSTOMER_DATE";

    /** B/C类：每个配送点一张送货单（现状默认行为） */
    public static final String DELIVERY_POINT_DATE = "DELIVERY_POINT_DATE";

    private DeliveryScopeType() {
    }

    /**
     * 归一化：空/未知值回退默认策略 DELIVERY_POINT_DATE（s14 DDL 默认值同源）
     */
    public static String normalize(String scopeType) {
        return CUSTOMER_DATE.equals(scopeType) || DELIVERY_POINT_DATE.equals(scopeType)
                ? scopeType : DELIVERY_POINT_DATE;
    }

    /**
     * 是否 A类客户日总单
     */
    public static boolean isCustomerDate(String scopeType) {
        return CUSTOMER_DATE.equals(StringUtils.trim(scopeType));
    }
}

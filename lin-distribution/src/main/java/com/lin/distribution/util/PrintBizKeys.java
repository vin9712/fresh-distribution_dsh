package com.lin.distribution.util;

import com.lin.common.exception.ServiceException;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 打印主体键（bizKey）解析/格式化工具（PT-1，《客户日报表打印优化设计》§3.1）
 *
 * <p>契约（与 PrintController 签发、PrintTicketPayload 绑定共用，双向唯一）：</p>
 * <ul>
 *   <li>{@code matrix:{customerId}:{deliveryDate}}——总单矩阵（客户日维度，printForm=MATRIX）；</li>
 *   <li>{@code point:{customerId}:{customerDeptId}:{deliveryDate}}——点单平铺（printForm=FLAT）。</li>
 * </ul>
 *
 * <p>签发侧（issueByBizKey）与解析侧（resolveByBizKey / 回执登记）都经由本类，
 * 保证字符串格式永不漂移；新增打印主体形态时在此扩展（如 {@code daily:{date}} 全客户日汇总）。</p>
 *
 * @author dsh
 */
public final class PrintBizKeys {

    /** 打印形态：总单矩阵（客户日） */
    public static final String TYPE_MATRIX = "matrix";
    /** 打印形态：点单平铺（客户+点+日） */
    public static final String TYPE_POINT = "point";

    private PrintBizKeys() {
    }

    /** 总单主体键：matrix:{customerId}:{deliveryDate} */
    public static String matrix(Long customerId, String deliveryDate) {
        require(customerId, deliveryDate);
        return TYPE_MATRIX + ":" + customerId + ":" + deliveryDate;
    }

    /** 点单主体键：point:{customerId}:{customerDeptId}:{deliveryDate} */
    public static String point(Long customerId, Long customerDeptId, String deliveryDate) {
        require(customerId, deliveryDate);
        if (customerDeptId == null) {
            throw new ServiceException("点单打印主体键须包含配送点");
        }
        return TYPE_POINT + ":" + customerId + ":" + customerDeptId + ":" + deliveryDate;
    }

    /**
     * 解析打印主体键
     *
     * @param bizKey 打印主体键
     * @return 解析结果（type/customerId/customerDeptId/deliveryDate）
     * @throws ServiceException 非法主体键（未知前缀/段数不符/非数字/日期不可解析）
     */
    public static BizKeyInfo parse(String bizKey) {
        if (StringUtils.isBlank(bizKey)) {
            throw new ServiceException("打印主体键不能为空");
        }
        String[] parts = bizKey.trim().split(":");
        if (TYPE_MATRIX.equals(parts[0])) {
            if (parts.length != 3) {
                throw new ServiceException("非法的总单打印主体键: " + bizKey);
            }
            return new BizKeyInfo(TYPE_MATRIX, toLong(parts[1], bizKey), null, toDate(parts[2], bizKey));
        }
        if (TYPE_POINT.equals(parts[0])) {
            if (parts.length != 4) {
                throw new ServiceException("非法的点单打印主体键: " + bizKey);
            }
            return new BizKeyInfo(TYPE_POINT, toLong(parts[1], bizKey), toLong(parts[2], bizKey), toDate(parts[3], bizKey));
        }
        throw new ServiceException("不支持的打印主体键: " + bizKey);
    }

    private static void require(Long customerId, String deliveryDate) {
        if (customerId == null) {
            throw new ServiceException("打印主体键须包含客户");
        }
        if (StringUtils.isBlank(deliveryDate)) {
            throw new ServiceException("打印主体键须包含配送日期");
        }
    }

    private static Long toLong(String value, String bizKey) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ServiceException("打印主体键含非法ID: " + bizKey);
        }
    }

    private static String toDate(String value, String bizKey) {
        try {
            // 规范化为 ISO 日期（容错 yyyy-MM-dd 后带时间的情况）
            return LocalDate.parse(value.trim().substring(0, 10)).toString();
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            throw new ServiceException("打印主体键含非法日期: " + bizKey);
        }
    }

    /** 打印主体键解析结果 */
    public static class BizKeyInfo {
        private final String type;
        private final Long customerId;
        private final Long customerDeptId;
        private final String deliveryDate;

        public BizKeyInfo(String type, Long customerId, Long customerDeptId, String deliveryDate) {
            this.type = type;
            this.customerId = customerId;
            this.customerDeptId = customerDeptId;
            this.deliveryDate = deliveryDate;
        }

        public String getType() {
            return type;
        }

        public Long getCustomerId() {
            return customerId;
        }

        public Long getCustomerDeptId() {
            return customerDeptId;
        }

        public String getDeliveryDate() {
            return deliveryDate;
        }

        /** 对应 JimuReport 打印形态：matrix→MATRIX / point→FLAT */
        public String printForm() {
            return TYPE_MATRIX.equals(type) ? "MATRIX" : "FLAT";
        }
    }
}

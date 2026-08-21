package com.lin.distribution.service;

/**
 * 通用业务单号服务（DB 序列，Redis 非硬依赖）
 *
 * @author dsh
 */
public interface BizCodeService {

    /**
     * 原子自增并返回新序列值（并发安全，依赖 biz_code_seq 主键 + ON DUPLICATE KEY 原子性）
     */
    long nextSeq(String bizKey);

    /**
     * 读取当前序列值（不自增），用于单号预览
     */
    long peekSeq(String bizKey);

    /**
     * 生成按日重置的单号：prefix + yyyyMMdd + 定长序号
     */
    String nextDailyCode(String bizType, String prefix, int seqLen);

    /**
     * 预览按日单号（不自增）
     */
    String peekDailyCode(String bizType, String prefix, int seqLen);

    /**
     * 生成标准SKU全局唯一编码：S + 8位数字（S00000001）
     */
    String nextSkuCode();

    /**
     * 生成客户商品编码：C{客户ID} + 6位自增序号
     */
    String nextCustomerSkuCode(Long customerId);
}

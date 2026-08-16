package com.lin.distribution.mapper;

/**
 * 通用业务序列表 Mapper（DB 序列替代 Redis，Redis 降为非硬依赖）
 *
 * @author dsh
 */
public interface BizCodeSeqMapper {

    /**
     * 原子自增：UPDATE biz_code_seq SET seq = LAST_INSERT_ID(seq + 1) WHERE biz_key = #{bizKey}
     *
     * @return 受影响行数（0 表示该 key 不存在）
     */
    int incrementSeq(String bizKey);

    /**
     * 首次插入（INSERT IGNORE，竞争失败返回 0）
     *
     * @return 受影响行数
     */
    int insertSeq(String bizKey);

    /**
     * 读取当前连接会话的 LAST_INSERT_ID()（须与 incrementSeq 在同一事务/连接内调用）
     */
    Long lastInsertId();

    /**
     * 读取当前序列值（不自增），用于单号预览
     */
    Long selectSeq(String bizKey);
}

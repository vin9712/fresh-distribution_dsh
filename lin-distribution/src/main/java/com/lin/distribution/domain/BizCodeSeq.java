package com.lin.distribution.domain;

import lombok.Data;

import java.util.Date;

/**
 * 通用业务序列表 biz_code_seq 对应实体
 *
 * @author dsh
 */
@Data
public class BizCodeSeq {

    /** 序列键（bizType:yyyyMMdd 或 bizType:ownerId） */
    private String bizKey;

    /** 当前序列值 */
    private Long seq;

    /** 更新时间 */
    private Date updateTime;
}

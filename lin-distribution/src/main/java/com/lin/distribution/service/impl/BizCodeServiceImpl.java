package com.lin.distribution.service.impl;

import com.lin.distribution.mapper.BizCodeSeqMapper;
import com.lin.distribution.service.BizCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 通用业务单号服务实现
 * 序列键约定：按日序列 bizType:yyyyMMdd；按归属对象序列 bizType:ownerId
 * 并发策略：UPDATE ... SET seq = LAST_INSERT_ID(seq+1) 原子自增；
 *           key 不存在时 INSERT IGNORE 建立首行（seq=1），与并发线程竞争失败则重试自增。
 *
 * @author dsh
 */
@Service
@RequiredArgsConstructor
public class BizCodeServiceImpl implements BizCodeService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final BizCodeSeqMapper bizCodeSeqMapper;

    @Transactional
    @Override
    public long nextSeq(String bizKey) {
        for (; ; ) {
            if (bizCodeSeqMapper.incrementSeq(bizKey) > 0) {
                Long seq = bizCodeSeqMapper.lastInsertId();
                return (seq == null || seq == 0L) ? 1L : seq;
            }
            if (bizCodeSeqMapper.insertSeq(bizKey) > 0) {
                // 本线程建立首行，序列为 1
                return 1L;
            }
            // 与并发线程竞争首行失败，重试自增
        }
    }

    @Override
    public long peekSeq(String bizKey) {
        Long seq = bizCodeSeqMapper.selectSeq(bizKey);
        return seq == null ? 0L : seq;
    }

    @Override
    public String nextDailyCode(String bizType, String prefix, int seqLen) {
        String date = LocalDate.now().format(DATE_FMT);
        long seq = nextSeq(bizType + ":" + date);
        return prefix + date + String.format("%0" + seqLen + "d", seq);
    }

    @Override
    public String peekDailyCode(String bizType, String prefix, int seqLen) {
        String date = LocalDate.now().format(DATE_FMT);
        long seq = peekSeq(bizType + ":" + date);
        return prefix + date + String.format("%0" + seqLen + "d", seq);
    }
}

package com.lin.distribution.service.impl;

import com.lin.common.utils.DateUtils;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.mapper.PrintTemplateMapper;
import com.lin.distribution.service.PrintTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 打印模板Service业务层处理
 *
 * @author lin
 * @date 2024-12-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintTemplateServiceImpl implements PrintTemplateService {
    private final PrintTemplateMapper printTemplateMapper;
    private final RedissonClient redissonClient;

    /**
     * 查询打印模板
     *
     * @param id 打印模板主键
     * @return 打印模板
     */
    @Override
    public PrintTemplate selectPrintTemplateById(Long id) {
        return printTemplateMapper.selectPrintTemplateById(id);
    }

    /**
     * 查询打印模板列表
     *
     * @param printTemplate 打印模板
     * @return 打印模板
     */
    @Override
    public List<PrintTemplate> selectPrintTemplateList(PrintTemplate printTemplate) {
        return printTemplateMapper.selectPrintTemplateList(printTemplate);
    }

    /**
     * 新增打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    @Override
    public PrintTemplate insertPrintTemplate(PrintTemplate printTemplate) {
        printTemplate.setCreateTime(DateUtils.getNowDate());
        printTemplateMapper.insertPrintTemplate(printTemplate);
        genPrintTemplateNo(true);
        return printTemplate;
    }

    /**
     * 修改打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    @Override
    public int updatePrintTemplate(PrintTemplate printTemplate) {
        printTemplate.setUpdateTime(DateUtils.getNowDate());
        return printTemplateMapper.updatePrintTemplate(printTemplate);
    }

    /**
     * 批量删除打印模板
     *
     * @param ids 需要删除的打印模板主键
     * @return 结果
     */
    @Override
    public int deletePrintTemplateByIds(Long[] ids) {
        return printTemplateMapper.deletePrintTemplateByIds(ids);
    }

    /**
     * 删除打印模板信息
     *
     * @param id 打印模板主键
     * @return 结果
     */
    @Override
    public int deletePrintTemplateById(Long id) {
        return printTemplateMapper.deletePrintTemplateById(id);
    }

    @Override
    public String generatePrintTemplateNo(Boolean refresh, String currentCode) {
        return genPrintTemplateNo(refresh, currentCode);
    }

    private String genPrintTemplateNo(Boolean refresh) {
        return genPrintTemplateNo(refresh, null);
    }

    private String genPrintTemplateNo(Boolean refresh, String currentCode) {
        String prefix = "PT";
        RMap<String, Integer> rMap = redissonClient.getMap("printTemplate");
        // get current redis seq
        int redisSeq = rMap.getOrDefault(prefix, 0);
        String redisQuoteCode = prefix + String.format("%05d", redisSeq);
        // if current code = redis code, return
        if (StringUtils.equals(redisQuoteCode, currentCode)) {
            return redisQuoteCode;
        }

        int seqNbr = BooleanUtils.isTrue(refresh) ? rMap.addAndGet(prefix, 1) : redisSeq;
        String seqNbrStr = String.format("%05d", seqNbr);
        return prefix + seqNbrStr;
    }

}

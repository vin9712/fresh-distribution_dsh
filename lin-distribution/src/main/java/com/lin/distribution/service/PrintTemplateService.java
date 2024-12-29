package com.lin.distribution.service;

import com.lin.distribution.domain.PrintTemplate;

import java.util.List;

/**
 * 打印模板Service接口
 *
 * @author lin
 * @date 2024-12-18
 */
public interface PrintTemplateService {
    /**
     * 查询打印模板
     *
     * @param id 打印模板主键
     * @return 打印模板
     */
    PrintTemplate selectPrintTemplateById(Long id);

    /**
     * 查询打印模板列表
     *
     * @param printTemplate 打印模板
     * @return 打印模板集合
     */
    List<PrintTemplate> selectPrintTemplateList(PrintTemplate printTemplate);

    /**
     * 新增打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    PrintTemplate insertPrintTemplate(PrintTemplate printTemplate);

    /**
     * 修改打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    int updatePrintTemplate(PrintTemplate printTemplate);

    /**
     * 批量删除打印模板
     *
     * @param ids 需要删除的打印模板主键集合
     * @return 结果
     */
    int deletePrintTemplateByIds(Long[] ids);

    /**
     * 删除打印模板信息
     *
     * @param id 打印模板主键
     * @return 结果
     */
    int deletePrintTemplateById(Long id);

    /**
     * 生成打印模板编号
     *
     * @param refresh       是否刷新
     * @param currentCode   当前编号
     * @return 打印模板编号
     */
    String generatePrintTemplateNo(Boolean refresh, String currentCode);
}

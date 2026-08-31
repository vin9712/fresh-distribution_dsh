package com.lin.distribution.mapper;

import java.util.List;

import com.lin.distribution.domain.PrintTemplate;
import org.apache.ibatis.annotations.Param;

/**
 * 打印模板Mapper接口
 *
 * @author lin
 * @date 2024-12-18
 */
public interface PrintTemplateMapper {
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
     * 三级绑定解析（DESIGN.md：客户+配送点组合 > 客户 > 全局默认；停用不参与）
     *
     * @param customerId      客户ID
     * @param deliveryPointId 配送点ID
     * @return 命中的模板（无则 null）
     */
    PrintTemplate selectBindTemplate(@Param("customerId") Long customerId, @Param("deliveryPointId") Long deliveryPointId);

    /**
     * 查询 JimuReport（积木报表）设计器中可用报表清单（del_flag=0，按更新时间倒序）
     *
     * @return [{id, name, code, updateTime}]
     */
    List<java.util.Map<String, Object>> selectJimuReports();

    /**
     * 新增打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    int insertPrintTemplate(PrintTemplate printTemplate);

    /**
     * 修改打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    int updatePrintTemplate(PrintTemplate printTemplate);

    /**
     * 删除打印模板
     *
     * @param id 打印模板主键
     * @return 结果
     */
    int deletePrintTemplateById(Long id);

    /**
     * W0-6：统计 content 包含某关键字的模板数（资源引用扫描，删除前校验）
     */
    int countContentLike(String keyword);

    /**
     * 批量删除打印模板
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deletePrintTemplateByIds(Long[] ids);
}

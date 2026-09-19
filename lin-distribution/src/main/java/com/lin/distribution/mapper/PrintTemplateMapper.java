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
     * 打印模板绑定解析（P1/D-048 显式化）：按 打印形态 + 绑定层级 路由。
     *
     * <p>两层维度 + 层级解析（服务层保证顺序正确）：
     * <ul>
     *   <li><b>打印形态</b> printForm：MATRIX=跨点总单 / FLAT=点单平铺（只匹配同形态模板；
     *       历史未配形态的模板视为 FLAT 兼容）；</li>
     *   <li><b>绑定层级</b> bind_type：1客户+配送点 &gt; 2客户 &gt; 3全局默认（仍按 SQL 里 bind_type 升序 limit 1）。</li>
     * </ul>
     * 修复：A 类总单 delivery_point_id=NULL 时不再靠 = NULL 的巧合，
     *       而是显式取 MATRIX 形态模板集，客户配的「跨点总单模板」能命中。</p>
     *
     * @param customerId      客户ID
     * @param deliveryPointId 配送点ID（总单传 NULL）
     * @param printForm       MATRIX=跨点总单 / FLAT=点单平铺
     * @return 命中的模板（无则 null）
     */
    PrintTemplate selectBindTemplate(@Param("customerId") Long customerId,
                                     @Param("deliveryPointId") Long deliveryPointId,
                                     @Param("printForm") String printForm);

    /**
     * 候选打印模板（P1/D-048）：客户可用的已发布模板，按绑定层级与印刷形态排序（供 print-candidates 接口，
     * 替代前端复制过滤）。命中「全局默认」时由服务层标记告警。
     *
     * @param customerId    客户ID（可为 null，取全局默认）
     * @param printForm     MATRIX=跨点总单 / FLAT=点单平铺
     * @return 已发布模板列表（bind_type 升序、同形态优先）
     */
    List<PrintTemplate> selectPrintCandidates(@Param("customerId") Long customerId,
                                              @Param("printForm") String printForm);

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
     * 批量删除打印模板
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deletePrintTemplateByIds(Long[] ids);
}

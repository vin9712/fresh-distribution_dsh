package com.lin.distribution.vo;

import com.lin.distribution.domain.PrintTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 按打印主体键（bizKey）解析打印模板的结果（PT-1，《客户日报表打印优化设计》§3.1）
 *
 * <p>替代前端硬编码模板ID：后端按 bizKey 前缀判打印形态（matrix→MATRIX / point→FLAT），
 * 复用 P1/D-048 三级绑定解析（客户+点 &gt; 客户 &gt; 全局默认，限定同形态已发布模板）。</p>
 *
 * <p>无已发布模板时 {@code templateId=null} 且带 {@code warning}（不抛 500，批量打印队列据此跳过该项）。</p>
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintTemplateResolveVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 打印形态 MATRIX=跨点总单 / FLAT=点单平铺 */
    private String printForm;

    /** 命中的打印模板ID（t_print_template.id）；null=未配置已发布模板 */
    private Long templateId;

    /** 命中的模板名称 */
    private String templateName;

    /** JimuReport 报表视图ID（模板 content 列），前端 open /jmreport/view/{reportViewId} */
    private String reportViewId;

    /** 是否命中「全局默认」模板（未配客户级/客户+点模板时 true，页面告警用） */
    private Boolean matchGlobalDefault;

    /** 同形态、该客户可用的已发布候选模板（bind_type 升序） */
    @Builder.Default
    private List<PrintTemplate> candidates = new ArrayList<>();

    /** 未命中时的提示文案（命中为 null） */
    private String warning;
}

package com.lin.distribution.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 打印模板版本快照对象 t_print_template_version（蓝图 W0-4.4）
 *
 * <p>已发布版本永久保留；发布/回滚均生成新版本（不覆盖历史），历史重打固定使用首次正式打印所用版本。</p>
 *
 * @author dsh
 */
@Data
public class PrintTemplateVersion implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 模板ID */
    private Long templateId;

    /** 版本号（自增） */
    private Integer versionNo;

    /** 模板名称快照 */
    private String name;

    /** 模板内容快照(JSON)；历史遗留：实际存的是 jimu_report.id（兼容期保留，勿再新增写入） */
    private String content;

    /**
     * 版式设计 JSON 快照（jimu_report.json_str，PR-D2）
     *
     * <p>这是版本快照的<b>真实载荷</b>：发布时从物化报表读取，回滚/导出以此为源，
     * 修复「只快照报表ID、版式回滚不了」的缺陷。</p>
     */
    private String designJson;

    /** 该版本物化出的 JimuReport 报表ID（审计/复现用） */
    private String reportId;

    /** 数据接线快照（契约版本/端点/参数；P2 起填充，导出/迁移自包含） */
    private String datasetSpec;

    /** 绑定类型快照：1客户+配送点组合 2客户 3全局默认 */
    private Integer bindType;

    /** 打印形态快照（P1/D-048）：MATRIX=跨点总单 / FLAT=点单平铺 */
    private String printForm;

    /** 客户ID快照（可空） */
    private Long customerId;

    /** 绑定配送点ID快照（可空） */
    private Long deliveryPointId;

    /** 联数快照 */
    private Integer copies;

    /** 发布人 */
    private String publishedBy;

    /** 发布时间 */
    private Date publishedTime;

    /** 版本说明（回滚时记录来源版本） */
    private String remark;
}

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

    /** 模板内容快照(JSON) */
    private String content;

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

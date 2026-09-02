package com.lin.distribution.domain;

import com.baomidou.mybatisplus.annotation.Version;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.*;

/**
 * 打印模板对象 t_print_template
 *
 * @author lin
 * @date 2024-12-18
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class PrintTemplate extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 客户ID（默认为0，表示通用模板）
     */
    @Excel(name = "客户ID", readConverterExp = "默=认为0，表示通用模板")
    private Long customerId;

    /**
     * 打印模板编号
     */
    @Excel(name = "打印模板编号")
    private String code;

    /**
     * 打印模板名称
     */
    @Excel(name = "打印模板名称")
    private String name;

    /**
     * 打印模板内容(json字符串)
     */
    private String content;

    /**
     * 打印模板类型，0-送货单，1-汇总表
     */
    @Excel(name = "打印模板类型，0-送货单，1-汇总表")
    private Integer type;

    /**
     * 渲染引擎（jimureport/hiprint，首版固定 jimureport）
     */
    private String renderEngine;

    /**
     * 绑定类型：1客户+配送点组合 2客户 3全局默认
     */
    @Excel(name = "绑定类型", readConverterExp = "1=客户+配送点组合,2=客户,3=全局默认")
    private Integer bindType;

    /**
     * 打印形态（P1/D-048）：MATRIX=跨点总单矩阵 / FLAT=点单平铺
     */
    @Excel(name = "打印形态", readConverterExp = "MATRIX=跨点总单矩阵,FLAT=点单平铺")
    private String printForm;

    /**
     * 绑定配送点ID（bind_type=1 时使用）
     */
    private Long deliveryPointId;

    /**
     * 联数（打印份数）
     */
    @Excel(name = "联数")
    private Integer copies;

    /**
     * 是否全局默认模板（0否 1是）
     */
    private String isDefault;

    /**
     * 模板状态（W0-4.4）：0草稿 1已测试 2已发布；已发布版本在下一版本发布前继续使用
     */
    private Integer status;

    /**
     * 是否测试水印（1是，测试打印整页水印，不计正式打印次数）
     */
    private Boolean testWatermark;

    /**
     * 逻辑删除
     */
    private Boolean isDeleted;

    /**
     * 版本号
     */
    @Version
    private Integer version;

}

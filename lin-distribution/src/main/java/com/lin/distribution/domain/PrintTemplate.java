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
     * 逻辑删除
     */
    private Boolean isDeleted;

    /**
     * 版本号
     */
    @Version
    private Integer version;

}

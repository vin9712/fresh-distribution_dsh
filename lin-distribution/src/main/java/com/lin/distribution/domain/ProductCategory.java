package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 商品分类对象 t_product_category
 * 
 * @author lin
 * @date 2024-11-02
 */
@Data
public class ProductCategory extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 分类名称 */
    @Excel(name = "分类名称")
    private String name;

    /** 上级分类ID */
    @Excel(name = "上级分类ID")
    private Long parentId;

    /** 分类编号 */
    @Excel(name = "分类编号")
    private String code;

    /** 分类级别(1级最大) */
    @Excel(name = "分类级别(1级最大)")
    private Integer level;

    /** 分类排序 */
    @Excel(name = "分类排序")
    private Integer sort;

    /** 逻辑删除 */
    @Excel(name = "逻辑删除")
    private Boolean isDeleted;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("name", getName())
            .append("parentId", getParentId())
            .append("code", getCode())
            .append("level", getLevel())
            .append("sort", getSort())
            .append("isDeleted", getIsDeleted())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}

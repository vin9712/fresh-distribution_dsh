package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 商品spu对象 t_product_spu
 *
 * @author lin
 * @date 2024-11-07
 */
@Data
public class    ProductSpu extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 分类ID
     */
    @Excel(name = "分类ID")
    private String categoryId;

    /**
     * 商品名称
     */
    @Excel(name = "商品名称")
    private String name;

    /**
     * 商品描述
     */
    @Excel(name = "商品描述")
    private String description;

    /**
     * 助记码
     */
    @Excel(name = "助记码")
    private String mnemonicCode;

    /**
     * 商品图片
     */
    @Excel(name = "商品图片")
    private String images;

    /**
     * 是否上架
     */
    @Excel(name = "是否上架")
    private Integer saleable;

    /**
     * 商品排序
     */
    @Excel(name = "商品排序")
    private Integer sort;

    /**
     * 是否有效
     */
    @Excel(name = "是否有效")
    private Integer valid;

    /**
     * 逻辑删除
     */
    @Excel(name = "逻辑删除")
    private Boolean isDeleted;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("categoryId", getCategoryId())
                .append("name", getName())
                .append("description", getDescription())
                .append("mnemonicCode", getMnemonicCode())
                .append("images", getImages())
                .append("saleable", getSaleable())
                .append("sort", getSort())
                .append("valid", getValid())
                .append("isDeleted", getIsDeleted())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}

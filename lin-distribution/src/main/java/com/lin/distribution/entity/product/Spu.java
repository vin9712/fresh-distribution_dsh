package com.lin.distribution.entity.product;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lin.common.core.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品表
 */
@EqualsAndHashCode(callSuper = true)
@ApiModel
@Data
@TableName(value = "t_spu")
public class Spu extends BaseEntity {
    @TableField(value = "title")
    @ApiModelProperty(value = "标题")
    private String title;
    @TableField(value = "sub_title")
    @ApiModelProperty(value = "副标题")
    private String subTitle;
    @TableField(value = "category_id")
    @ApiModelProperty(value = "分类ID")
    private Long categoryId;

    @TableField(value = "saleable")
    @ApiModelProperty(value = "是否上架")
    private Boolean saleable;
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "是否有效")
    private Boolean valid;
    @TableField(value = "is_delete")
    @ApiModelProperty(value = "逻辑删除")
    private Boolean isDelete;
}
package com.lin.distribution.entity.product;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.core.domain.BaseEntity;
import com.lin.common.exception.ServiceException;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Optional;

/**
 * 商品分类表
 * @author Lin
 * @date 2024/10/27
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
@TableName(value = "t_category")
public class SpuCategory extends BaseEntity {
    @TableField(value = "name")
    @ApiModelProperty(value = "分类名称")
    private String name;
    @TableField(value = "code")
    @ApiModelProperty(value = "分类编码")
    private String code;
    @TableField(value = "parent_id")
    @ApiModelProperty(value = "上级分类ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long parentId;
    @TableField(value = "level")
    @ApiModelProperty(value = "分类级别")
    private Integer level;
    @TableField(value = "sort")
    @ApiModelProperty(value = "排名指数")
    private Integer sort;
    @TableField(value = "is_deleted")
    @ApiModelProperty(value = "逻辑删除")
    private Boolean isDeleted;

    public static SpuCategory createNewCategory() {
        return new SpuCategory().setLevel(1)
                .setSort(1).setIsDeleted(false);
    }

    /**
     * 判断是否为一级分类
     */
    public void checkIsFirstLevel() {
        if (level != 1) {
            throw new ServiceException("当前分类【" + Optional.ofNullable(name).orElse("") + "】不是一级分类！");
        }
    }

}

package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import com.lin.common.utils.PinYinConvertUtils;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 客户对象 t_customer
 *
 * @author lin
 * @date 2024-11-08
 */
@Data
public class Customer extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 客户名称
     */
    @Excel(name = "客户名称")
    private String name;

    /**
     * 客户别名
     */
    @Excel(name = "客户别名")
    private String alias;

    /**
     * 客户类型
     */
    @Excel(name = "客户类型")
    private String type;

    /**
     * 手机号
     */
    private String tel;

    /**
     * 客户地址
     */
    private String address;

    /**
     * 是否有效
     */
    @Excel(name = "是否有效")
    private Integer valid;

    /**
     * 逻辑删除
     */
    private Boolean isDeleted;


    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id" , getId())
                .append("name" , getName())
                .append("alias" , getAlias())
                .append("type" , getType())
                .append("tel" , getTel())
                .append("address" , getAddress())
                .append("valid" , getValid())
                .append("isDeleted" , getIsDeleted())
                .append("createBy" , getCreateBy())
                .append("createTime" , getCreateTime())
                .append("updateBy" , getUpdateBy())
                .append("updateTime" , getUpdateTime())
                .append("remark" , getRemark())
                .toString();
    }

    public String getShowName(){
        return StringUtils.isNotEmpty(alias) ? alias : name;
    }

    public String getShowMnemonicCode(){
        return PinYinConvertUtils.toFirstChar(getShowName());
    }
}

package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 客户部门对象 t_customer_dept
 *
 * @author lin
 * @date 2024-11-09
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerDept extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 客户ID
     */
    @Excel(name = "客户ID")
    private Long customerId;

    /**
     * 上级部门ID
     */
    private Long parentId;

    /**
     * 客户部门编号
     */
    private String code;

    /**
     * 部门名称
     */
    @Excel(name = "部门名称")
    private String name;

    /**
     * 助记码
     */
    private String mnemonicCode;

    /**
     * 客户配送地址
     */
    private String address;

    /**
     * 位置坐标
     */
    private String location;

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
                .append("id", getId())
                .append("customerId", getCustomerId())
                .append("parentId", getParentId())
                .append("name", getName())
                .append("mnemonicCode", getMnemonicCode())
                .append("address", getAddress())
                .append("location", getLocation())
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

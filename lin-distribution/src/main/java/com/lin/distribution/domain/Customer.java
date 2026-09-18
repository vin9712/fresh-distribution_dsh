package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import com.lin.common.utils.PinYinConvertUtils;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Locale;

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
     * 客户分组ID（关联 customer_group.id）
     */
    private Long groupId;

    /**
     * 客户类型
     */
    @Excel(name = "客户类型", dictType = "t_customer_type")
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
    private Integer valid;

    /**
     * 逻辑删除
     */
    private Boolean isDeleted;

    /**
     * 组单策略（S14/D-015：CUSTOMER_DATE 客户总单 / DELIVERY_POINT_DATE 按点成单，客户级配置）
     */
    private String docScopeType;

    /**
     * 相同商品合并成行（客户级配置，总单打印时同商品多订单行合并）
     */
    private Boolean docMergeSameItem;

    /**
     * 是否启用班次（客户级配置，仅大长江）：开启后下单需选班次，矩阵/总单按「配送点×班次」出列
     */
    private Boolean shiftEnabled;


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
                .append("docScopeType" , getDocScopeType())
                .append("docMergeSameItem" , getDocMergeSameItem())
                .append("shiftEnabled" , getShiftEnabled())
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
        return PinYinConvertUtils.toFirstChar(getShowName()).toUpperCase(Locale.ROOT);
    }
}

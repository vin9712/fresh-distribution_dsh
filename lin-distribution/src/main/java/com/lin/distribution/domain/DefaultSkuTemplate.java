package com.lin.distribution.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 默认SKU模板对象 default_sku_template（批量赋值用，不含价格）
 *
 * @author dsh
 */
@Data
public class DefaultSkuTemplate implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 模板名称 */
    private String name;

    /** 适用客户分组ID（可空=全部） */
    private Long customerGroupId;

    /** 状态（1启用 0停用） */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
}

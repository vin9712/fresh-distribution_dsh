package com.lin.distribution.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 报价模板-客户绑定对象 price_template_customer
 * 约束：一个客户最多绑定一个模板（UNIQUE(customer_id)）
 *
 * @author dsh
 */
@Data
public class PriceTemplateCustomer implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 模板ID */
    private Long templateId;

    /** 客户ID */
    private Long customerId;

    /** 客户名称（关联查询展示，非表字段） */
    private String customerName;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}

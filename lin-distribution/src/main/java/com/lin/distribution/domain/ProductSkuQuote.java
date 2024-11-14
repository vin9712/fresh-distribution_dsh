package com.lin.distribution.domain;

import java.time.LocalDate;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.*;

/**
 * 商品报价对象 t_product_sku_quote
 *
 * @author lin
 * @date 2024-11-14
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class ProductSkuQuote extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 客户ID */
    @Excel(name = "客户ID")
    private Long customerId;

    /** 报价编号 */
    private String code;

    /** 报价生效时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "报价生效时间", width = 30, dateFormat = "yyyy-MM-dd")
    private LocalDate effectiveStartDate;

    /** 报价结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "报价结束时间", width = 30, dateFormat = "yyyy-MM-dd")
    private LocalDate effectiveEndDate;

    /** 是否有效 */
    @Excel(name = "是否有效")
    private Integer valid;

    /** 逻辑删除 */
    private Boolean isDeleted;

    /** 版本号 */
    @Version
    private Integer version;

    /**
     * 查询条件: 报价创建日期
     */
    @TableField(exist = false)
    private String createTimeStart;
    @TableField(exist = false)
    private String createTimeEnd;

}
